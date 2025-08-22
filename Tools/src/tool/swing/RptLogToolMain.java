package tool.swing;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Date;
import java.util.Map;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.commons.lang3.StringUtils;
import org.jdesktop.swingx.JXDatePicker;

import tool.Main;
import tool.logic.RptLogTool;
import tool.logic.RptLogTool.RptLog;
import tool.logic.TableBackup;

public class RptLogToolMain {

	private RptLogToolParm tp;
	private String jdbcNam;
	private RptLogTool rptLogTool;

	public static class RptLogToolParm {

		private JXDatePicker datePicker;

		private JComboBox<String> comboBox;

		private JTextField actnCde;
		private JTextField userId;
		private JTextField maxData;

		private DefaultListModel<RptLog> logList;
		private JList<RptLog> JList;

		private JTextArea rptReportParms;

		private JTextArea paramMap;

		public JXDatePicker getDatePicker() {
			return datePicker;
		}

		public void setDatePicker(JXDatePicker datePicker) {
			this.datePicker = datePicker;
		}

		public JComboBox<String> getComboBox() {
			return comboBox;
		}

		public void setComboBox(JComboBox<String> comboBox) {
			this.comboBox = comboBox;
		}

		public JTextField getActnCde() {
			return actnCde;
		}

		public void setActnCde(JTextField actnCde) {
			this.actnCde = actnCde;
		}

		public DefaultListModel<RptLog> getLogList() {
			return logList;
		}

		public void setLogList(DefaultListModel<RptLog> logList) {
			this.logList = logList;
		}

		public JList<RptLog> getJList() {
			return JList;
		}

		public void setJList(JList<RptLog> jList) {
			JList = jList;
		}

		public JTextField getUserId() {
			return userId;
		}

		public void setUserId(JTextField userId) {
			this.userId = userId;
		}

		public JTextField getMaxData() {
			return maxData;
		}

		public void setMaxData(JTextField maxData) {
			this.maxData = maxData;
		}

	}

	public JPanel initialize(Font f) {

		JPanel jp = new JPanel() {

			/**
			 * 
			 */
			private static final long serialVersionUID = 6731564037673689825L;

			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);

				g.setFont(f);
				g.setColor(Color.RED);
				g.drawString("DB 選擇:", 10, 30);
				g.drawString("列印日期:", 200, 30);
				g.setColor(Color.BLACK);
				g.drawString("功能代碼:", 400, 30);
				g.setColor(Color.RED);
				g.drawString("列印人員:", 10, 65);
				g.drawString("資料筆數:", 180, 65);
				g.drawString("RPT_REPORT_PARMS:", 10, 380);
				g.drawString("PARAM_MAP:", 400, 380);
//				g.drawString("SQL執行順序2:(刪除SQL)", 10, 440);
//				g.drawString("SQL執行順序3:(新增SQL，還原資料)", 10, 580);

			}
		};

		jp.setLayout(null); // 禁用布局管理器
		tp = new RptLogToolParm();
		// DB選擇
		String[] items = Main.dataSourceMap.keySet().toArray(new String[Main.dataSourceMap.size()]);
		tp.comboBox = new JComboBox<>(items);
		tp.comboBox.setBounds(80, 10, 100, 30);
		jp.add(tp.comboBox);

		tp.datePicker = new JXDatePicker();
		tp.datePicker.setDate(new Date());
		tp.datePicker.setFormats(new java.text.SimpleDateFormat("yyyy/MM/dd"));
		tp.datePicker.setBounds(270, 10, 100, 30);
		jp.add(tp.datePicker);

		tp.actnCde = new JTextField();
		tp.actnCde.setBounds(470, 10, 90, 30);
		tp.actnCde.setText("");
		jp.add(tp.actnCde);

		tp.userId = new JTextField();
		tp.userId.setBounds(80, 45, 90, 30);
		tp.userId.setText(Main.USER_ID);
		jp.add(tp.userId);

		tp.maxData = new JTextField();
		tp.maxData.setBounds(250, 45, 90, 30);
		tp.maxData.setText("100");
		jp.add(tp.maxData);

		JButton btnGetRptLog = new JButton("查詢");
		ListenerSearch listenerSearch = new ListenerSearch();
		btnGetRptLog.addActionListener(listenerSearch);
		btnGetRptLog.setBounds(650, 45, 80, 30);
		btnGetRptLog.setOpaque(false);
		btnGetRptLog.setFont(f);
		jp.add(btnGetRptLog);

		tp.logList = new DefaultListModel<RptLog>();
		tp.JList = new JList<>(tp.logList);
		tp.JList.setFont(new Font("Monospaced", Font.PLAIN, 15));
		tp.JList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		JScrollPane sp = new JScrollPane(tp.JList);
		sp = new JScrollPane(tp.JList);
		sp.setBounds(10, 100, 750, 250);
		tp.JList.addListSelectionListener(new ListenerChangeLog());
		jp.add(sp);

		tp.rptReportParms = new JTextArea();
		JScrollPane sp1 = new JScrollPane(tp.rptReportParms);
		tp.rptReportParms.setText("");
		sp1.setBounds(10, 400, 350, 350);
		sp1.setFont(f);
		jp.add(sp1);

		tp.paramMap = new JTextArea();
		JScrollPane sp2 = new JScrollPane(tp.paramMap);
		tp.paramMap.setText("");
		sp2.setBounds(410, 400, 350, 350);
		sp2.setFont(f);
		jp.add(sp2);

		return jp;
	}

	class ListenerChangeLog implements ListSelectionListener {
		@Override
		public void valueChanged(ListSelectionEvent e) {
			if (!e.getValueIsAdjusting()) { // 避免觸發兩次
				if (null != tp.JList.getSelectedValue() && null != tp.JList.getSelectedValue().getOid()) {
					Map<String, Object> map = tp.JList.getSelectedValue().getLogMap();
					StringBuffer sb = new StringBuffer();
					for (String key : map.keySet()) {
						if (StringUtils.equals(key, "sqlFormatted")) {
							sb.append("SQL(已替換參數)");
						} else {
							sb.append(key);
						}
						sb.append("：");
						sb.append(map.get(key));
						sb.append("\r\n");
					}
					tp.rptReportParms.setText(sb.toString());
					map = tp.JList.getSelectedValue().getParamMap();
					sb = new StringBuffer();
					for (String key : map.keySet()) {
						if (StringUtils.equals(key, "sqlFormatted")) {
							sb.append("SQL(已替換參數)");
						} else {
							sb.append(key);
						}
						sb.append("：");
						sb.append(map.get(key));
						sb.append("\r\n");
					}
					tp.paramMap.setText(sb.toString());
				}
			} else {
				tp.rptReportParms.setText("");
				tp.paramMap.setText("");
			}

		}
	};

	class ListenerSearch implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			if (null == rptLogTool || !StringUtils.equals(tp.comboBox.getSelectedItem().toString(), jdbcNam)) {
				if (null != rptLogTool) {
					rptLogTool.close();
				}
				rptLogTool = new RptLogTool(Main.dataSourceMap.get(tp.comboBox.getSelectedItem()));
				jdbcNam = tp.comboBox.getSelectedItem().toString();
			}
			tp.rptReportParms.setText("");
			tp.paramMap.setText("");
			rptLogTool.prcs(tp);

		}
	}

}
