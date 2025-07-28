import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;

import org.apache.poi.xwpf.usermodel.XWPFComment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpecMain {
	/**
	 * 
	 */
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	private JTextPane txtSql;

	private JTextField textField;
	
	private JTextField infoField;

	private JComboBox<String> comboBox;
	JFrame frame = new JFrame("Word 註解查看器");

	public JPanel initialize(Font f) {

		JPanel jp = new JPanel() {
			/**
			 * 
			 */
			private static final long serialVersionUID = -2973622889706778416L;

			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);

				g.setFont(f);
				g.setColor(Color.RED);
				g.drawString("SPEC註解日期:", 10, 30);
//				g.drawString("JAVA info Name:", 290, 30);
//				g.drawString("DB 選擇:", 530, 30);
//				g.drawString("增加註解方式: 在欄位後面加@", 500, 750);
			}
		};

		jp.setLayout(null); // 禁用布局管理器

		this.textField = new JTextField();
		this.textField.setText("2025/06/19");
		this.textField.setBounds(180, 10, 100, 30);
		this.textField.setFont(f);
		jp.add(this.textField, "North");
		this.textField.setColumns(10);
		
//		this.infoField = new JTextField();
//		this.infoField.setText("info");
//		this.infoField.setBounds(410, 10, 100, 30);
//		this.infoField.setFont(f);
//		jp.add(this.infoField, "North");
//		this.infoField.setColumns(10);

//		this.txtSql = new JTextPane();
//		JScrollPane sp = new JScrollPane(txtSql);
//		this.txtSql.setText("SELECT \r\n CONTR_NO @ \r\n FROM AM_C_MST a @");
//		sp.setBounds(15, 50, 750, 630);
//		sp.setFont(f);
//		jp.add(sp);

		JButton btnConvert = new JButton("選擇 Word 檔案 (.docx)");
		ListenerFile listenerFile = new ListenerFile();
		btnConvert.addActionListener(listenerFile);
		btnConvert.setBounds(100, 700, 200, 30);
		btnConvert.setOpaque(false);
		btnConvert.setFont(f);
		jp.add(btnConvert);

		JButton btnConvert2 = new JButton("搜尋異動SQL");
		ListenerSPEC listenerSPEC = new ListenerSPEC();
		btnConvert2.addActionListener(listenerSPEC);
		btnConvert2.setBounds(500, 700, 200, 30);
		btnConvert2.setOpaque(false);
		btnConvert2.setFont(f);
		jp.add(btnConvert2);

		// 建立下拉選單項目
		String[] items = Main.dataSourceMap.keySet().toArray(new String[Main.dataSourceMap.size()]);

		// 建立 JComboBox
		comboBox = new JComboBox<>(items);
		comboBox.setBounds(600, 10, 100, 30);
		jp.add(comboBox);

		return jp;
	}
	
	private void genWord() {

		
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 300);
        frame.setLayout(new BorderLayout());

        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);

        JButton button = new JButton("選擇 Word 檔案 (.docx)");
        button.addActionListener((ActionEvent e) -> {
            JFileChooser chooser = new JFileChooser();
            int result = chooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                
            }
        });

        frame.add(button, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.setVisible(true);
	}

	class ListenerFile implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			JFileChooser chooser = new JFileChooser();
            int result = chooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                
            }
		}
	}

	class ListenerSPEC implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			SqlFormat sqlFormat = new SqlFormat();
			try {
				Main.getNames(Main.dataSourceMap.get(comboBox.getSelectedItem()));
				SpecMain.this.txtSql.setText(sqlFormat.genSqlForSPEC(txtSql.getText(),
						Main.tableNamesMap, Main.tableColumnsMap));
			} catch (Exception e1) {
				SpecMain.this.txtSql.setText(e1.getMessage());
			}
		}
	}

}
