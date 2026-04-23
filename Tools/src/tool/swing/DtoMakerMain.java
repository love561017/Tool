package tool.swing;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tool.Main;
import tool.logic.DtoMaker;

public class DtoMakerMain {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 前綴對應類別名稱（選填）。
	 * 格式："criteria:AmAct05005Dto,amFeeCfgMst:AmFeeCfgMstDto"
	 * 留空時自動以首字大寫+Dto命名，例如 criteria → CriteriaDto
	 */
	private JTextField nameMapping;

	/** 輸入：舊版 JSP 內容 */
	private JTextPane txtInput;

	/** 輸出：產生的 DTO 程式碼 */
	private JTextPane txtOutput;

	public JPanel initialize(Font f) {

		JPanel jp = new JPanel() {
			private static final long serialVersionUID = -2973622889706778416L;

			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				g.setFont(f);
				g.setColor(Color.RED);
				g.drawString("前綴對應(選填):", 10, 30);
				g.setColor(Color.GRAY);
				g.setFont(new Font("微軟正黑體", 0, 11));
				g.drawString("格式: criteria:AmAct05005Dto,amFeeCfgMst:AmFeeCfgMstDto  (留空=自動命名)", 10, 760);
			}
		};

		jp.setLayout(null);

		// 前綴對應輸入
		this.nameMapping = new JTextField("");
		this.nameMapping.setBounds(140, 8, 600, 28);
		this.nameMapping.setFont(f);
		jp.add(this.nameMapping);

		// ---- 輸入區 ----
		JLabel inputLabel = new JLabel("JSP 內容 (貼入舊版 JSP):");
		inputLabel.setBounds(15, 48, 300, 20);
		inputLabel.setFont(f);
		jp.add(inputLabel);

		this.txtInput = new JTextPane();
		JScrollPane spInput = new JScrollPane(txtInput);
		spInput.setBounds(15, 72, 750, 300);
		jp.add(spInput);

		// ---- 按鈕 ----
		JButton btnGenerate = new JButton("產生 DTO");
		btnGenerate.addActionListener(new ListenerGenerate());
		btnGenerate.setBounds(270, 385, 150, 30);
		btnGenerate.setFont(f);
		jp.add(btnGenerate);

		JButton btnClear = new JButton("清除");
		btnClear.addActionListener(e -> {
			txtInput.setText("");
			txtOutput.setText("");
		});
		btnClear.setBounds(435, 385, 80, 30);
		btnClear.setFont(f);
		jp.add(btnClear);

		// ---- 輸出區 ----
		JLabel outputLabel = new JLabel("產生的 DTO 程式碼:");
		outputLabel.setBounds(15, 425, 300, 20);
		outputLabel.setFont(f);
		jp.add(outputLabel);

		this.txtOutput = new JTextPane();
		this.txtOutput.setEditable(false);
		this.txtOutput.setBackground(new Color(245, 245, 245));
		JScrollPane spOutput = new JScrollPane(txtOutput);
		spOutput.setBounds(15, 448, 750, 300);
		jp.add(spOutput);

		return jp;
	}

	class ListenerGenerate implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			String jsp = txtInput.getText();
			if (jsp == null || jsp.trim().isEmpty()) {
				txtOutput.setText("請先貼入 JSP 內容。");
				return;
			}

			DtoMaker dtoMaker = new DtoMaker();
			try {
				// 嘗試取 DB 欄位說明（失敗不影響主功能）
				try {
					if (Main.dataSourceMap != null && !Main.dataSourceMap.isEmpty()) {
						String firstDs = Main.dataSourceMap.values().iterator().next();
						Main.getNames(firstDs);
					}
				} catch (Exception ignored) {
				}

				String result = dtoMaker.process(
						jsp,
						nameMapping.getText().trim(),
						Main.tableColumnsMap.isEmpty() ? null : Main.tableColumnsMap);

				txtOutput.setText(result);

			} catch (Exception e1) {
				logger.error(e1.getMessage(), e1);
				txtOutput.setText("Error: " + e1.getMessage());
			}
		}
	}
}
