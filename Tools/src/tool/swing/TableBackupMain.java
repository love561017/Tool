package tool.swing;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;

import tool.Main;
import tool.logic.TableBackup;

public class TableBackupMain {
	private TableBackupParm tp;

	public static class TableBackupParm {
		private JTextArea txtSql;

		private JTextPane txtTestSql;

		private JTextPane txtDeleteSql;

		private JTextArea txtInsertSql;

		private JComboBox<String> comboBox;

		private JRadioButton rb1;
		private JRadioButton rb2;
		private JTextField path;

		private JRadioButton rb3;
		private JRadioButton rb4;

		public JTextArea getTxtSql() {
			return txtSql;
		}

		public void setTxtSql(JTextArea txtSql) {
			this.txtSql = txtSql;
		}

		public JTextPane getTxtTestSql() {
			return txtTestSql;
		}

		public void setTxtTestSql(JTextPane txtTestSql) {
			this.txtTestSql = txtTestSql;
		}

		public JTextPane getTxtDeleteSql() {
			return txtDeleteSql;
		}

		public void setTxtDeleteSql(JTextPane txtDeleteSql) {
			this.txtDeleteSql = txtDeleteSql;
		}

		public JTextArea getTxtInsertSql() {
			return txtInsertSql;
		}

		public void setTxtInsertSql(JTextArea txtInsertSql) {
			this.txtInsertSql = txtInsertSql;
		}

		public JComboBox<String> getComboBox() {
			return comboBox;
		}

		public void setComboBox(JComboBox<String> comboBox) {
			this.comboBox = comboBox;
		}

		public JRadioButton getRb1() {
			return rb1;
		}

		public void setRb1(JRadioButton rb1) {
			this.rb1 = rb1;
		}

		public JRadioButton getRb2() {
			return rb2;
		}

		public void setRb2(JRadioButton rb2) {
			this.rb2 = rb2;
		}

		public JTextField getPath() {
			return path;
		}

		public void setPath(JTextField path) {
			this.path = path;
		}

		public JRadioButton getRb3() {
			return rb3;
		}

		public void setRb3(JRadioButton rb3) {
			this.rb3 = rb3;
		}

		public JRadioButton getRb4() {
			return rb4;
		}

		public void setRb4(JRadioButton rb4) {
			this.rb4 = rb4;
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
				g.drawString("是否產生媒體檔(大量資料用):", 200, 30);
				g.drawString("產生路徑:", 470, 30);
				g.drawString("是否產生新OID:", 10, 60);
				g.drawString("備份資料SQL(不換行，換行表示另一到SQL，暫不支援JOIN):", 10, 100);
				g.drawString("SQL執行順序1:(此為測試SQL，執行失敗不可往下執行)", 10, 300);
				g.drawString("SQL執行順序2:(刪除SQL)", 10, 440);
				g.drawString("SQL執行順序3:(新增SQL，還原資料)", 10, 580);

			}
		};

		jp.setLayout(null); // 禁用布局管理器
		tp = new TableBackupParm();
		// DB選擇
		String[] items = Main.dataSourceMap.keySet().toArray(new String[Main.dataSourceMap.size()]);
		tp.comboBox = new JComboBox<>(items);
		tp.comboBox.setBounds(80, 10, 100, 30);
		jp.add(tp.comboBox);

		ButtonGroup group = new ButtonGroup();
		tp.rb1 = new JRadioButton("是");
		tp.rb2 = new JRadioButton("否");
		tp.rb2.setSelected(true);
		group.add(tp.rb1);
		group.add(tp.rb2);
		tp.rb1.setBounds(385, 10, 40, 30);
		tp.rb2.setBounds(425, 10, 40, 30);
		jp.add(tp.rb1);
		jp.add(tp.rb2);

		tp.path = new JTextField();
		tp.path.setBounds(540, 10, 80, 30);
		tp.path.setText("D:\\DbBackup");
		jp.add(tp.path);
		
		ButtonGroup group2 = new ButtonGroup();
		tp.rb3 = new JRadioButton("是");
		tp.rb4 = new JRadioButton("否");
		tp.rb4.setSelected(true);
		group2.add(tp.rb3);
		group2.add(tp.rb4);
		tp.rb3.setBounds(115, 40, 40, 30);
		tp.rb4.setBounds(155, 40, 40, 30);
		jp.add(tp.rb3);
		jp.add(tp.rb4);

		tp.txtSql = new JTextArea();
		JScrollPane sp = new JScrollPane(tp.txtSql);
		tp.txtSql.setText(
				"SELECT * FROM AM_C_FEE_AP WHERE FEE_CMPT_SN IN ( SELECT FEE_CMPT_SN FROM AM_C_FEE_CMPT_MST WHERE CONTR_NO IN( 'B-JASON-0001','B-JASON-0002')); \r\nSELECT * FEE_CMPT_SN FROM AM_C_FEE_CMPT_MST WHERE CONTR_NO IN( 'B-JASON-0001','B-JASON-0002') \r\n ***注意刪除順序 \r\n ");
		sp.setBounds(15, 110, 750, 150);
		sp.setFont(f);
		jp.add(sp);

		tp.txtTestSql = new JTextPane();
		JScrollPane sp1 = new JScrollPane(tp.txtTestSql);
		tp.txtTestSql.setText("***不用輸入***");
		tp.txtTestSql.setEditable(false);
		sp1.setBounds(15, 310, 750, 100);
		sp1.setFont(f);
		jp.add(sp1);

		tp.txtDeleteSql = new JTextPane();
		JScrollPane sp2 = new JScrollPane(tp.txtDeleteSql);
		tp.txtDeleteSql.setText("***不用輸入***");
		tp.txtDeleteSql.setEditable(false);
		sp2.setBounds(15, 450, 750, 100);
		sp2.setFont(f);
		jp.add(sp2);

		tp.txtInsertSql = new JTextArea();
		JScrollPane sp3 = new JScrollPane(tp.txtInsertSql);
		tp.txtInsertSql.setText("***不用輸入***");
		tp.txtInsertSql.setEditable(false);
		sp3.setBounds(15, 590, 750, 100);
		sp3.setFont(f);
		jp.add(sp3);

		JButton btnConvert2 = new JButton("產生指令");
		Listener listenerSPEC = new Listener();
		btnConvert2.addActionListener(listenerSPEC);
		btnConvert2.setBounds(500, 700, 200, 30);
		btnConvert2.setOpaque(false);
		btnConvert2.setFont(f);
		jp.add(btnConvert2);

		return jp;
	}

	class Listener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			TableBackup tableBackup = new TableBackup(Main.dataSourceMap.get(tp.comboBox.getSelectedItem()));
			try {
				tp.txtInsertSql.setText("");
				tableBackup.prcs(tp);
				tp.txtTestSql.setText(tableBackup.getTest().toString());
				tp.txtDeleteSql.setText(tableBackup.getDelete().toString());
			} finally {
				tableBackup.close();
			}

		}
	}

}
