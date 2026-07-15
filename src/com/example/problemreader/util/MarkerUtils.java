package com.example.problemreader.util;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MarkerUtils {

    public static String getLineContentFromMarker(IMarker marker) {
        // 1. 从 Marker 中获取对应的文件资源 (IResource 转换为 IFile)
        if (marker.getResource() instanceof IFile) {
            IFile file = (IFile) marker.getResource();
            
            try {
                // 2. 获取 Marker 对应的行号 (注意：Marker 的行号是从 1 开始的)
                int targetLineNum = marker.getAttribute(IMarker.LINE_NUMBER, -1);
                
                if (targetLineNum <= 0) {
                    return "无法获取行号（可能该错误未关联具体行）";
                }

                // 3. 读取文件内容并定位到目标行
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(file.getContents(), file.getCharset()))) {
                    
                    String lineContent;
                    int currentLineNum = 1;
                    
                    while ((lineContent = reader.readLine()) != null) {
                        if (currentLineNum == targetLineNum) {
                            return lineContent.trim(); // 返回整行内容（去除了两端空格）
                        }
                        currentLineNum++;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                return "读取文件内容失败: " + e.getMessage();
            }
        }
        return "该 Marker 未关联到有效的 IFile 资源";
    }
}
