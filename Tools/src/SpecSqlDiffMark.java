import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import javax.swing.JOptionPane;

import org.apache.poi.xwpf.usermodel.XWPFComment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

public class SpecSqlDiffMark {

	
	public String prcs(File file) {
		
		try (FileInputStream fis = new FileInputStream(file)) {
            XWPFDocument doc = new XWPFDocument(fis);

            XWPFComment[] comments = doc.getComments();
            StringBuilder output = new StringBuilder();

            if (comments != null && comments.length !=0) {
                for (XWPFComment comment : comments) {
                    output.append("註解作者: ").append(comment.getAuthor()).append("\n");
                    output.append("註解內容: ").append(comment.getText()).append("\n");
                    output.append("--------------------------------------------------\n");
                }
            } else {
                output.append("此檔案中沒有註解。\n");
            }

            return output.toString();
        } catch (Exception ex) {
            ex.printStackTrace();
            return "讀取 Word 檔案失敗：" + ex.getMessage();
        }
	}
}
