package org.lpw.tephra.office.pptx.parser;

import com.alibaba.fastjson.JSONObject;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.lpw.tephra.office.pptx.MediaWriter;

/**
 * 解析器。
 *
 * @author lpw
 */
public interface Parser {
    /**
     * 获取处理顺序。
     *
     * @return 处理顺序。
     */
    int getSort();

    /**
     * 解析数据。
     *
     * @param xslfShape   形状。
     * @param mediaWriter 媒体资源输出器。
     * @param shape       解析数据。
     */
    void parse(XSLFShape xslfShape, MediaWriter mediaWriter, JSONObject shape);
}
