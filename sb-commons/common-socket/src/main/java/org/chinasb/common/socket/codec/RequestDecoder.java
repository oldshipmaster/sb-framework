package org.chinasb.common.socket.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.Attribute;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.chinasb.common.socket.SessionManager;
import org.chinasb.common.socket.context.ApplicationContext;
import org.chinasb.common.socket.firewall.ClientType;
import org.chinasb.common.socket.firewall.Firewall;
import org.chinasb.common.socket.message.Request;
import org.chinasb.common.socket.message.Response;
import org.chinasb.common.socket.type.ResponseCode;
import org.chinasb.common.socket.type.SessionType;
import org.chinasb.common.utility.HashUtility;

/**
 * 消息解码器
 * Packet = [short]PacketHeader(2 bytes) + [int]BodyLength(4 bytes) + [bytes]PacketBody(n bytes)
 * PacketBody: DataHeader{[int]authCode(4 bytes) + [int]sn(4 bytes) + [byte]messageType(1 byte) +
 * [int]module(4 bytes) + [int]cmd(4 bytes)}(20 bytes) + [bytes]DataBody(n bytes)
 * @author zhujuan
 *
 */
public class RequestDecoder extends ByteToMessageDecoder {

    private static final Log LOGGER = LogFactory.getLog(RequestDecoder.class);

    /**
     * 字符集
     */
    public static final Charset charset = Charset.forName("UTF-8");
    /**
     * 包头标识
     */
    public static final short PACKAGE_HEADER_ID = 0x71ab;
    /**
     * 包头固定长度：标识(2字节) + 长度(4字节)
     */
    public static final int PACKAGE_HEADER_LEN = 6;
    /**
     * 数据包头长度
     */
    public static final int HEADER_LEN = 17;
    /**
     * 忽略校验码字节数量
     */
    public static final int IGNORE_AUTH_CODE_BYTES = 4;
    /**
     * SOCKET安全策略请求
     */
    private final byte[] POLICY_REQUEST = "<policy-file-request/>".getBytes(charset);
    /**
     * SOCKET安全策略内容
     */
    public static final byte[] policyResponse = "".getBytes(charset);

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 通过解码上下文信息处理继续接收完整的数据包，优化解码过程
        Attribute<CodecContext> codecContextAttribute = ctx.attr(SessionType.CODEC_CONTEXT_KEY);
        CodecContext codecContext = codecContextAttribute.get();
        if ((codecContext != null) && (codecContext.isSameState(DecoderState.WAITING_DATA))) {
            // 等待数据包所需字节数量
            if (in.readableBytes() < codecContext.getBytesNeeded()) {
                return;
            }
            // 读取数据
            byte[] buffer = new byte[codecContext.getBytesNeeded()];
            in.readBytes(buffer);
            // 解码
            Request reqest = decodeBuffer(ctx, buffer);
            if (reqest != null) {
                out.add(reqest);
            }
            // 进入解码就绪状态，等待新的解码开始
            codecContext.setState(DecoderState.READY);
            // 移除解码上下文信息
            codecContextAttribute.remove();
            return;
        }

        // 客户端首次数据请求处理
        Attribute<Boolean> firstRequestAttribute = ctx.attr(SessionType.FIRST_REQUEST_KEY);
        Boolean firstRequest = firstRequestAttribute.get();
        if (firstRequest == null) {
            firstRequest = Boolean.valueOf(true);
            firstRequestAttribute.set(firstRequest);
        }
        // 首次请求发送SOCKET安全策略
        if (firstRequest.booleanValue()) {
            in.markReaderIndex();
            if (!in.isReadable()) {
                in.resetReaderIndex();
                return;
            }
            byte firstByte = in.readByte();
            in.resetReaderIndex();
            if (firstByte == 60) {
                if (in.readableBytes() < POLICY_REQUEST.length) {
                    return;
                }
                in.markReaderIndex();
                byte[] byteArray = new byte[POLICY_REQUEST.length];
                in.readBytes(byteArray);
                if (Arrays.equals(byteArray, POLICY_REQUEST)) {
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info(String.format("SESSION[%s] 发送SOCKET安全策略...",
                                new Object[] {String.valueOf(ctx.channel().id())}));
                    }
                    firstRequest = Boolean.valueOf(false);
                    firstRequestAttribute.set(firstRequest);
                    ctx.channel().writeAndFlush(policyResponse);
                    return;
                }
                in.resetReaderIndex();
            }
            firstRequest = Boolean.valueOf(false);
            firstRequestAttribute.set(firstRequest);
        }
        // 处理包头解码（如果包头存在异常，读掉找到包头前的所有字节）
        for (;;) {
            if (in.readableBytes() < PACKAGE_HEADER_LEN) {
                return;
            }
            in.markReaderIndex();
            if (in.readShort() == PACKAGE_HEADER_ID) {
                break;
            }
            in.resetReaderIndex();
            in.readByte();
        }
        // 处理数据包大小限制
        int len = in.readInt();
        if ((len <= 0) || (len >= 65536)) {
            LOGGER.error(String.format("Body length: %d", new Object[] {Integer.valueOf(len)}));
            return;
        }
        // 处理等待数据包所需字节数量，如果第一次接收数据包不完整则设置解码上下文信息，优化下一次解码过程
        if (in.readableBytes() < len) {
            codecContext = CodecContext.valueOf(len, DecoderState.WAITING_DATA);
            codecContextAttribute.set(codecContext);
            return;
        }
        // 读取数据
        byte[] buffer = new byte[len];
        in.readBytes(buffer);
        // 解码
        Request request = decodeBuffer(ctx, buffer);
        if (request != null) {
            out.add(request);
        }
    }

    /**
     * 解码数据包
     * @param buffer
     * @param ctx
     * @return
     */
    public Request decodeBuffer(ChannelHandlerContext ctx, byte[] buffer) {
        if (buffer == null) {
            LOGGER.error("buffer 为空异常");
            return null;
        }
        int bufferSize = buffer.length;
        if (bufferSize < HEADER_LEN) {
            LOGGER.error(String.format("协议解析错误, 数据长度小于包头长度 [bufferSize: %d, HEADER_LEN: %d]",
                    new Object[] {Integer.valueOf(bufferSize), Integer.valueOf(HEADER_LEN)}));
            return null;
        }
        DataInputStream dataInputStream = null;
        ByteArrayInputStream byteArrayInputStream = null;
        byte[] authData = Arrays.copyOfRange(buffer, IGNORE_AUTH_CODE_BYTES, bufferSize);
        try {
            byteArrayInputStream = new ByteArrayInputStream(buffer);
            dataInputStream = new DataInputStream(byteArrayInputStream);
            int authCode = dataInputStream.readInt();
            int sn = dataInputStream.readInt();
            byte messageType = dataInputStream.readByte();
            int module = dataInputStream.readInt();
            int cmd = dataInputStream.readInt();
            int calcAuthCode = (int) HashUtility.fnv32(authData, 0, authData.length);
            if (authCode != calcAuthCode) {
                Channel session = ctx.channel();
                LOGGER.error(String
                        .format("协议解析FVN Hash不匹配: [sn: %d, module: %d, cmd: %d, authCode: %d, calcAuthCode:%d]",
                                new Object[] {Integer.valueOf(sn), Integer.valueOf(module),
                                        Integer.valueOf(cmd), Integer.valueOf(authCode),
                                        Integer.valueOf(calcAuthCode)}));
                ChannelFuture future = session.writeAndFlush(Response.valueOf(sn, module, cmd, messageType,
                        ResponseCode.RESPONSE_CODE_AUTH_CODE_ERROR));
                ApplicationContext context = ctx.attr(SessionType.APPLICATION_CONTEXT_KEY).get();
                if (context != null) {
                    Firewall firewall = context.getBean(Firewall.class);
                    if ((firewall.getClientType(session) != ClientType.MIS)
                            && (firewall.blockedByAuthCodeErrors(session, 1))) {
                        SessionManager sessionManager = context.getBean(SessionManager.class);
                        if (sessionManager != null) {
                            String ip = sessionManager.getRemoteIp(session);
                            LOGGER.error(String.format("In blacklist: [ip: %s]", new Object[] {ip}));
                            future.addListener(ChannelFutureListener.CLOSE);
                        }
                    }
                }
                return null;
            }
            Request request = Request.valueOf(sn, module, cmd, messageType);
            if (bufferSize > HEADER_LEN) {
                byte[] byteArray = new byte[bufferSize - HEADER_LEN];
                dataInputStream.read(byteArray);
                Object value = transferObject(module, cmd, messageType, byteArray);
                if (value == null) {
                    LOGGER.error(String.format(
                            "解析协议错误: [sn: %d, module: %d, cmd: %d]",
                            new Object[] {Integer.valueOf(sn), Integer.valueOf(module),
                                    Integer.valueOf(cmd)}));
                    ctx.channel().writeAndFlush(
                            Response.valueOf(sn, module, cmd, messageType,
                                    ResponseCode.RESPONSE_CODE_RESOLVE_ERROR));
                    return null;
                }
                request.setValue(value);
            }
            LOGGER.debug(request);
            return request;
        } catch (Exception ex) {
            LOGGER.error("解码异常: ", ex);
        } finally {
            if (dataInputStream != null) {
                try {
                    dataInputStream.close();
                } catch (IOException ex) {
                    LOGGER.error("DataInputStream.close() error: " + ex.getMessage());
                }
            }
            if (byteArrayInputStream != null) {
                try {
                    byteArrayInputStream.close();
                } catch (IOException ex) {
                    LOGGER.error("ByteArrayInputStream.close() error: " + ex.getMessage());
                }
            }
            dataInputStream = null;
            byteArrayInputStream = null;
        }
        return null;
    }

    /**
     * 转换消息对象
     * @param module 功能模块
     * @param cmd 模块指令
     * @param messageType 消息类型
     * @param array 消息数据
     * @return
     */
    protected Object transferObject(int module, int cmd, int messageType, byte[] array) {
        if (messageType == MessageType.STRING.ordinal()) {
            return new String(array, charset);
        }
        if (messageType == MessageType.JAVA.ordinal()) {
            return ObjectCodec.byteArray2Object(array);
        }
        if (messageType == MessageType.AMF3.ordinal()) {
            return ObjectCodec.byteArray2ASObject(array);
        }
        return null;
    }
}
