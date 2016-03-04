package org.fastcatsearch.datasource.reader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

import javax.xml.bind.JAXBException;

import org.fastcatsearch.ir.common.IRException;
import org.fastcatsearch.ir.document.Document;
import org.fastcatsearch.ir.settings.SchemaSetting;
import org.fastcatsearch.util.JAXBConfigs;
import org.junit.Test;

public class StoredDocumentSourceReaderTest {

	@Test
	public void test() throws JAXBException, IRException, IOException, InterruptedException {
		final String indexPath = "/Users/swsong/TEST_HOME/danawa1022/node1/collections/VM/data/index1";
		String schemaFilePath = "/Users/swsong/TEST_HOME/danawa1022/node1/collections/VM/schema.xml";
		
		InputStream is = new FileInputStream(schemaFilePath);
		final SchemaSetting schemaSetting = JAXBConfigs.readConfig(is, SchemaSetting.class);
		is.close();
		
		int count = 3;
		
		final CountDownLatch latch = new CountDownLatch(count);
		
		for (int k = 0; k < count; k++) {
			Thread t = new Thread() {
				@Override
				public void run() {
					File file = new File(indexPath);
					StoredDocumentSourceReader reader;
					try {
						reader = new StoredDocumentSourceReader(file, schemaSetting);
					
						reader.init();
						int i = 0;
						long st = System.currentTimeMillis();
						while(reader.hasNext()) {
							Document document = reader.nextDocument();
							if(i % 10000 == 0) {
								System.out.println(i++ + "... " + (System.currentTimeMillis() -st) + "ms");
								st = System.currentTimeMillis();
							}
							i++;
						}
						reader.close();
					
					} catch (IRException e) {
						e.printStackTrace();
					}
					latch.countDown();
				}
			};
			
			t.start();
			
		}
		
		latch.await();
	}
	
}
