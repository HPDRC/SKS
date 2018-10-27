/*
 * MalformedRecordException.java
 *
 */

package sks.dataset;

public class MalformedRecordException extends Exception {
	public MalformedRecordException(String message)	{
    super(message);
	}
	
	public MalformedRecordException(String message, Throwable cause) {
    super(message, cause);
	}
}