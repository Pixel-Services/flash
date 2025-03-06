package com.pixelervices.flash.tests;

import com.pixelervices.flash.BaseTest;
import com.pixelervices.flash.utils.FileUploader;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.Assert.*;

public class FileHandlerTest extends BaseTest {

    @Test
    public void testFileHandler() {
        try {
            File testFile = createInMemoryFile("This is a test file");
            String response = FileUploader.uploadFile("http://localhost:8080/test/file", testFile);
            assertEquals("This is a test file", response);
        } catch (Exception e) {
            fail("Failed to send file: " + e.getMessage());
        }
    }

    private File createInMemoryFile(String content) {
        try {
            File file = File.createTempFile("test", ".txt");
            file.deleteOnExit();
            FileWriter writer = new FileWriter(file);
            writer.write(content);
            writer.close();
            return file;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create in-memory file", e);
        }
    }
}