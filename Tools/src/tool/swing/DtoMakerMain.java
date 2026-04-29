package tool.swing;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	private static final Preferences PREFS = Preferences.userNodeForPackage(DtoMakerMain.class);
	private static final String PREF_ENTITY_PATH = "dtomaker.entityPath";

	private static final Pattern ENTITY_FIELD_PAT = Pattern.compile(
			"private\\s+(\\S+)\\s+(\\w+)\\s*;");

	/**
	 * 前綴對應類別名稱（選填）。
	 * 格式："criteria:AmAct05005Dto,amFeeCfgMst:AmFeeCfgMstDto"
	 */
	private JTextField nameMapping;

	/** Entity 所在目錄路徑（持久保存）。例：D:\eclipse_all\lab\src\com\ddsc\am\entity */
	private JTextField txtEntityPath;

	/** Entity 類別名稱，逗號分隔。例：AmTxBuyStk,AmFeeCfgMst */
	private JTextField txtEntityNames;

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

		// ---- 前綴對應 ----
		this.nameMapping = new JTextField("");
		this.nameMapping.setBounds(140, 8, 600, 28);
		this.nameMapping.setFont(f);
		jp.add(this.nameMapping);

		// ---- Entity 路徑 ----
		JLabel entityPathLabel = new JLabel("Entity 路徑 (選填):");
		entityPathLabel.setBounds(15, 44, 160, 20);
		entityPathLabel.setFont(new Font("微軟正黑體", 0, 12));
		entityPathLabel.setForeground(Color.RED);
		jp.add(entityPathLabel);

		this.txtEntityPath = new JTextField(PREFS.get(PREF_ENTITY_PATH, ""));
		this.txtEntityPath.setBounds(175, 44, 590, 28);
		this.txtEntityPath.setFont(f);
		jp.add(this.txtEntityPath);

		// ---- Entity 名稱 ----
		JLabel entityNamesLabel = new JLabel("Entity 名稱 (逗號分隔):");
		entityNamesLabel.setBounds(15, 80, 200, 20);
		entityNamesLabel.setFont(new Font("微軟正黑體", 0, 12));
		entityNamesLabel.setForeground(Color.RED);
		jp.add(entityNamesLabel);

		this.txtEntityNames = new JTextField("");
		this.txtEntityNames.setBounds(215, 80, 550, 28);
		this.txtEntityNames.setFont(f);
		jp.add(this.txtEntityNames);

		// ---- JSP 輸入區 ----
		JLabel inputLabel = new JLabel("JSP 內容 (貼入舊版 JSP):");
		inputLabel.setBounds(15, 116, 300, 20);
		inputLabel.setFont(f);
		jp.add(inputLabel);

		this.txtInput = new JTextPane();
		JScrollPane spInput = new JScrollPane(txtInput);
		spInput.setBounds(15, 140, 750, 210);
		jp.add(spInput);

		// ---- 按鈕 ----
		JButton btnGenerate = new JButton("產生 DTO");
		btnGenerate.addActionListener(new ListenerGenerate());
		btnGenerate.setBounds(270, 358, 150, 30);
		btnGenerate.setFont(f);
		jp.add(btnGenerate);

		JButton btnClear = new JButton("清除");
		btnClear.addActionListener(e -> {
			txtEntityNames.setText("");
			txtInput.setText("");
			txtOutput.setText("");
		});
		btnClear.setBounds(435, 358, 80, 30);
		btnClear.setFont(f);
		jp.add(btnClear);

		// ---- 輸出區 ----
		JLabel outputLabel = new JLabel("產生的 DTO 程式碼:");
		outputLabel.setBounds(15, 396, 300, 20);
		outputLabel.setFont(f);
		jp.add(outputLabel);

		this.txtOutput = new JTextPane();
		this.txtOutput.setEditable(false);
		this.txtOutput.setBackground(new Color(245, 245, 245));
		JScrollPane spOutput = new JScrollPane(txtOutput);
		spOutput.setBounds(15, 420, 750, 330);
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

			// 持久化路徑
			String entityPath = txtEntityPath.getText().trim();
			PREFS.put(PREF_ENTITY_PATH, entityPath);

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
						Main.tableColumnsMap.isEmpty() ? null : Main.tableColumnsMap,
						loadEntityFieldTypes(entityPath, txtEntityNames.getText().trim()));

				txtOutput.setText(result);

			} catch (Exception e1) {
				logger.error(e1.getMessage(), e1);
				txtOutput.setText("Error: " + e1.getMessage());
			}
		}

		/**
		 * 從指定目錄讀取 entity .java 檔，解析所有 private 欄位的型別。
		 * 同名欄位以最後一個 entity 為準。
		 */
		private Map<String, String> loadEntityFieldTypes(String dirPath, String entityNames) {
			if (dirPath.isEmpty() || entityNames.isEmpty()) return null;
			Map<String, String> map = new LinkedHashMap<>();
			for (String name : entityNames.split(",")) {
				name = name.trim();
				if (name.isEmpty()) continue;
				File file = new File(dirPath, name + ".java");
				if (!file.exists()) {
					logger.warn("找不到 entity 檔案: {}", file.getAbsolutePath());
					continue;
				}
				try {
					String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
					Matcher m = ENTITY_FIELD_PAT.matcher(content);
					while (m.find()) {
						String type = m.group(1);
						String field = m.group(2);
						if (type.contains(".")) {
							type = type.substring(type.lastIndexOf('.') + 1);
						}
						map.put(field, type);
					}
				} catch (Exception ex) {
					logger.error("讀取 entity 失敗: {}", file.getAbsolutePath(), ex);
				}
			}
			return map.isEmpty() ? null : map;
		}
	}
}
