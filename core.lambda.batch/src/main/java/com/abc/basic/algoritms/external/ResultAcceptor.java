/**
 * 
 */
package com.abc.basic.algoritms.external;

import java.io.IOException;

/**
 * @author yovn
 *
 */
public interface ResultAcceptor {
	
	
	void start()throws IOException;
	void end()throws IOException;
	void acceptRecord(Record rec)throws IOException;

}
