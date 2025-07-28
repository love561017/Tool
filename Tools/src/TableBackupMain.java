import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

public class TableBackupMain {
	private JTextPane txtSql;

	private JTextPane txtTestSql;

	private JTextPane txtDeleteSql;

	private JTextPane txtInsertSql;

	private JComboBox<String> comboBox;

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
				g.drawString("備份資料SQL(不換行，換行表示另一到SQL，暫不支援JOIN):", 10, 70);
				g.drawString("SQL執行順序1:(此為測試SQL，執行失敗不可往下執行)", 10, 300);
				g.drawString("SQL執行順序2:(刪除SQL)", 10, 440);
				g.drawString("SQL執行順序3:(新增SQL，還原資料)", 10, 580);

			}
		};

		jp.setLayout(null); // 禁用布局管理器

		// DB選擇
		String[] items = Main.dataSourceMap.keySet().toArray(new String[Main.dataSourceMap.size()]);
		comboBox = new JComboBox<>(items);
		comboBox.setBounds(80, 10, 100, 30);
		jp.add(comboBox);

		this.txtSql = new JTextPane();
		JScrollPane sp = new JScrollPane(txtSql);
		this.txtSql.setText(
				"SELECT * FROM AM_C_FEE_AP WHERE FEE_CMPT_SN IN ( SELECT FEE_CMPT_SN FROM AM_C_FEE_CMPT_MST WHERE CONTR_NO IN( 'B-JASON-0001','B-JASON-0002')); \r\nSELECT * FEE_CMPT_SN FROM AM_C_FEE_CMPT_MST WHERE CONTR_NO IN( 'B-JASON-0001','B-JASON-0002') \r\n ***注意刪除順序 \r\n ");
		sp.setBounds(15, 80, 750, 180);
		sp.setFont(f);
		jp.add(sp);

		this.txtTestSql = new JTextPane();
		JScrollPane sp1 = new JScrollPane(txtTestSql);
		this.txtTestSql.setText("***不用輸入***");
		txtTestSql.setEditable(false);
		sp1.setBounds(15, 310, 750, 100);
		sp1.setFont(f);
		jp.add(sp1);

		this.txtDeleteSql = new JTextPane();
		JScrollPane sp2 = new JScrollPane(txtDeleteSql);
		this.txtDeleteSql.setText("***不用輸入***");
		txtDeleteSql.setEditable(false);
		sp2.setBounds(15, 450, 750, 100);
		sp2.setFont(f);
		jp.add(sp2);

		this.txtInsertSql = new JTextPane();
		JScrollPane sp3 = new JScrollPane(txtInsertSql);
		this.txtInsertSql.setText("***不用輸入***");
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
			TableBackup tableBackup = new TableBackup(Main.dataSourceMap.get(comboBox.getSelectedItem()));
			try {
				tableBackup.prcs(txtSql.getText());

				txtTestSql.setText(tableBackup.getTest().toString());
				txtDeleteSql.setText(tableBackup.getDelete().toString());
				txtInsertSql.setText(tableBackup.getSb().toString());
			} finally {
				tableBackup.close();
			}

		}
	}

}
