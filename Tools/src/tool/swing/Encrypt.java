package tool.swing;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTextPane;

import org.jasypt.util.text.BasicTextEncryptor;

public class Encrypt {

	private JTextField passWord;

	private JTextField userName;

	private JTextPane res;

	public JPanel initialize(Font f) {
		JPanel jp = new JPanel();
		jp.setLayout(null); // 禁用布局管理器
		this.userName = new JTextField();
		this.userName.setText("帳號");
		this.userName.setBounds(15, 50, 375, 63);
		this.userName.setFont(f);
		jp.add(this.userName);
		this.passWord = new JTextField();
		this.passWord.setText("密碼");
		this.passWord.setBounds(15, 150, 375, 63);
		this.passWord.setFont(f);
		jp.add(this.passWord);
		this.res = new JTextPane();
		this.res.setText("結果");
		this.res.setBounds(15, 250, 375, 63);
		this.res.setFont(f);
		jp.add(this.res);
		JButton btnConvert = new JButton("加密");
		Listener listener = new Listener();
		btnConvert.addActionListener(listener);
		btnConvert.setBounds(50, 700, 200, 30);
		btnConvert.setOpaque(false);
		btnConvert.setFont(f);
		jp.add(btnConvert);
		JButton btnConvert2 = new JButton("解密");
		Listene2 listener2 = new Listene2();
		btnConvert2.addActionListener(listener2);
		btnConvert2.setBounds(550, 700, 200, 30);
		btnConvert2.setOpaque(false);
		btnConvert2.setFont(f);
		jp.add(btnConvert2);
		return jp;
	}

	class Listener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			Execute sqlFormat = new Execute();
			Encrypt.this.res.setText(sqlFormat.exc(Encrypt.this.userName.getText(), Encrypt.this.passWord.getText()));
		}
	}

	class Listene2 implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			Execute sqlFormat = new Execute();
			Encrypt.this.res
					.setText(sqlFormat.decrypt(Encrypt.this.userName.getText(), Encrypt.this.passWord.getText()));
		}
	}

	public class Execute {
		public String exc(String user, String password) {
			BasicTextEncryptor bte = new BasicTextEncryptor();
			bte.setPassword(user);
			return bte.encrypt(password);
		}

		public String decrypt(String user, String password) {
			BasicTextEncryptor bte = new BasicTextEncryptor();
			bte.setPassword(user);
			return bte.decrypt(password);
		}
	}
}
