package org.fastcatsearch.datasource.reader;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.esotericsoftware.yamlbeans.YamlWriter;
import org.fastcatsearch.ir.common.IRException;
import org.fastcatsearch.object.Contact;
import org.fastcatsearch.object.Phone;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by 전제현 on 2016-03-07.
 * Yaml 파일을 자바에서 다루는 연습용 테스트 클래스
 */
public class YamlFileTest {

    protected static Logger logger = LoggerFactory.getLogger(YamlFileTest.class);

    @Test
    public void loadYmlFile1() throws IRException {

        logger.info("==== loadYmlFile1 Start ====");

        try {

            YamlReader reader = new YamlReader(new FileReader("D:\\TEST_HOME\\test.yml"));
            Object object = reader.read();
            Map map = (Map) object;

            logger.info(object.toString());
            logger.info(map.get("name").toString());

        } catch (FileNotFoundException e) {
            logger.error(e.toString());
        } catch (YamlException e) {
            logger.error(e.toString());
        } finally {
            logger.info("==== loadYmlFile1 End ====");
        }
    }

    @Test
    public void loadYmlFile2() throws IRException {

        logger.info("==== loadYmlFile2 Start ====");

        try {

            YamlReader reader = new YamlReader(new FileReader("D:\\TEST_HOME\\test2.yml"));
            while (true) {
                Map contact = (Map) reader.read();
                if (contact == null) break;
                logger.info(contact.get("age").toString());
            }

        } catch (FileNotFoundException e) {
            logger.error(e.toString());
        } catch (YamlException e) {
            logger.error(e.toString());
        } finally {
            logger.info("==== loadYmlFile2 End ====");
        }
    }

    @Test
    public void loadYmlFile3() throws IRException {

        logger.info("==== loadYmlFile3 Start ====");

        try {

            YamlReader reader = new YamlReader(new FileReader("D:\\TEST_HOME\\test3.yml"));
            Contact contact = reader.read(Contact.class);
            System.out.println(contact.age);

        } catch (FileNotFoundException e) {
            logger.error(e.toString());
        } catch (YamlException e) {
            logger.error(e.toString());
        } finally {
            logger.info("==== loadYmlFile3 End ====");
        }
    }

    @Test
    public void loadYmlFile4() throws IRException {

        logger.info("==== loadYmlFile4 Start ====");

        try {

            Contact contact = new Contact();
            contact.name = "Nathan Sweet";
            contact.age = 28;

            List list1 = new ArrayList();
            list1.add("moo");
            list1.add("cow");

            List list2 = new LinkedList();
            list2.add("moo");
            list2.add("cow");

            YamlWriter writer = new YamlWriter(new FileWriter("D:\\TEST_HOME\\test4.yml"));
            writer.write(contact);
            writer.write(list1);
            writer.write(list2);
            writer.close();

        } catch (FileNotFoundException e) {
            logger.error(e.toString());
        } catch (YamlException e) {
            logger.error(e.toString());
        } catch (IOException e) {
            logger.error(e.toString());
        } finally {
            logger.info("==== loadYmlFile4 End ====");
        }
    }

    /*@Test
    public void loadYmlFile5() throws IRException {

        logger.info("==== loadYmlFile5 Start ====");

        try {

            YamlReader reader = new YamlReader(new FileReader("D:\\TEST_HOME\\test5.yml"));
            Contact contact = reader.read(Contact.class);
            Phone phone = reader.read(Phone.class);
            System.out.println(contact.age);
            System.out.println(phone.number);

        } catch (FileNotFoundException e) {
            logger.error(e.toString());
        } catch (YamlException e) {
            logger.error(e.toString());
        } catch (IOException e) {
            logger.error(e.toString());
        } finally {
            logger.info("==== loadYmlFile5 End ====");
        }
    }*/

    @Test
    public void loadYmlFile6() throws IRException {

        logger.info("==== loadYmlFile6 Start ====");

        try {

            YamlWriter writer = new YamlWriter(new FileWriter("D:\\TEST_HOME\\test6.yml"));
            Contact contact = new Contact();
            contact.name = "Nathan Sweet dddd";
            contact.age = 293;
            writer.getConfig().setClassTag("contact", Contact.class);
            writer.write(contact);
            writer.close();

        } catch (FileNotFoundException e) {
            logger.error(e.toString());
        } catch (YamlException e) {
            logger.error(e.toString());
        } catch (IOException e) {
            logger.error(e.toString());
        } finally {
            logger.info("==== loadYmlFile6 End ====");
        }
    }
}