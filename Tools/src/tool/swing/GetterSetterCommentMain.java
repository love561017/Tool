package tool.swing;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tool.logic.GetterSetterComment;

public class GetterSetterCommentMain {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/** 輸入：含欄位註解的 Java 類別 */
	private JTextPane txtInput;

	/** 輸出：getter/setter 都加上註解後的結果 */
	private JTextPane txtOutput;

	public JPanel initialize(Font f) {

		JPanel jp = new JPanel();
		jp.setLayout(null);

		// ---- 輸入區 ----
		JLabel inputLabel = new JLabel("Java 類別 (貼入含欄位註解的 Java 程式碼):");
		inputLabel.setBounds(15, 10, 450, 20);
		inputLabel.setFont(f);
		jp.add(inputLabel);

		this.txtInput = new JTextPane();
		JScrollPane spInput = new JScrollPane(txtInput);
		spInput.setBounds(15, 35, 750, 330);
		jp.add(spInput);

		// ---- 按鈕 ----
		JButton btnProcess = new JButton("補上 getter/setter 註解");
		btnProcess.addActionListener(new ListenerProcess());
		btnProcess.setBounds(220, 378, 240, 30);
		btnProcess.setFont(f);
		jp.add(btnProcess);

		JButton btnClear = new JButton("清除");
		btnClear.addActionListener(e -> {
			txtInput.setText("");
			txtOutput.setText("");
		});
		btnClear.setBounds(475, 378, 80, 30);
		btnClear.setFont(f);
		jp.add(btnClear);

		// ---- 輸出區 ----
		JLabel outputLabel = new JLabel("結果:");
		outputLabel.setBounds(15, 418, 100, 20);
		outputLabel.setFont(f);
		jp.add(outputLabel);

		this.txtOutput = new JTextPane();
		this.txtOutput.setEditable(false);
		this.txtOutput.setBackground(new Color(245, 245, 245));
		JScrollPane spOutput = new JScrollPane(txtOutput);
		spOutput.setBounds(15, 440, 750, 330);
		jp.add(spOutput);

		return jp;
	}

	class ListenerProcess implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			String code = txtInput.getText();
			if (code == null || code.trim().isEmpty()) {
				txtOutput.setText("請先貼入 Java 類別程式碼。");
				return;
			}
			try {
				GetterSetterComment gsc = new GetterSetterComment();
				txtOutput.setText(gsc.process(code));
			} catch (Exception ex) {
				logger.error(ex.getMessage(), ex);
				txtOutput.setText("Error: " + ex.getMessage());
			}
		}
	}
}
