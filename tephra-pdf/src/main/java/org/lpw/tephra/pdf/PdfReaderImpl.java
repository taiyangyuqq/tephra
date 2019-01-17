package org.lpw.tephra.pdf;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.lpw.tephra.pdf.parser.ImageParser;
import org.lpw.tephra.pdf.parser.TextParser;
import org.lpw.tephra.util.Logger;
import org.lpw.tephra.util.Numeric;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author lpw
 */
@Component("tephra.pdf.reader")
public class PdfReaderImpl implements PdfReader {
    @Inject
    private Numeric numeric;
    @Inject
    private Logger logger;

    @Override
    public JSONObject read(InputStream inputStream, MediaWriter mediaWriter) {
        JSONObject object = new JSONObject();
        JSONArray pages = new JSONArray();
        try (PDDocument pdDocument = PDDocument.load(inputStream)) {
            int size = pdDocument.getNumberOfPages();
            if (size == 0)
                return object;

            ImageParser imageParser = new ImageParser();
            for (int i = 0; i < size; i++) {
                PDPage pdPage = pdDocument.getPage(i);
                if (i == 0)
                    parseSize(object, pdPage);
                imageParser.processPage(pdPage);
            }

            TextParser textParser = new TextParser();
            textParser.getText(pdDocument);
        } catch (IOException e) {
            logger.warn(e, "解析PDF数据时发生异常！");
        }

        object.put("pages", pages);

        return object;
    }

    private void parseSize(JSONObject object, PDPage pdPage) {
        JSONObject size = new JSONObject();
        size.put("width", pointToPixel(pdPage.getCropBox().getWidth()));
        size.put("height", pointToPixel(pdPage.getCropBox().getHeight()));
        object.put("size", size);
    }

    private int pointToPixel(double point) {
        return numeric.toInt(point * 96 / 72);
    }
}