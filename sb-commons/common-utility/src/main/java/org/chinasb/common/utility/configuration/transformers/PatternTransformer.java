package org.chinasb.common.utility.configuration.transformers;

import java.lang.reflect.Field;
import java.util.regex.Pattern;

import org.chinasb.common.utility.configuration.PropertyTransformer;
import org.chinasb.common.utility.configuration.TransformationException;

/**
 * Authomatic pattern transformer for RegExp resolving
 * 
 * @author SoulKeeper
 */
@SuppressWarnings("unchecked")
public class PatternTransformer implements PropertyTransformer
{
	/**
	 * Shared instance of this transformer
	 */
	public static final PatternTransformer	SHARED_INSTANCE	= new PatternTransformer();

	/**
	 * Transforms String to Pattern object
	 * 
	 * @param value
	 *            value that will be transformed
	 * @param field
	 *            value will be assigned to this field
	 * @return Pattern Object
	 * @throws TransformationException
	 *             if pattern is not valid
	 */
	@Override
	public Pattern transform(String value, Field field) throws TransformationException
	{
		try
		{
			return Pattern.compile(value);
		}
		catch(Exception e)
		{
			throw new TransformationException("Not valid RegExp: " + value, e);
		}
	}
}