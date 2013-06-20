package com.afjk01.tool.MultiTouchPadServer;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

public class MultiTouchServer implements MouseListener
{
	protected static final String VERSION = "1.0.5";

	public boolean mIsDebug = false;
	
	private TrayIcon icon;
	private String mLocalHostName;
	private String mLocalHostIP;
	public static InetAddress localAddr;
	PopupMenu mPopupMenu;
	private int mPortNum;
	private ReceiveThread mReceiveThread;
	private ReceiveThreadUDP mReceiveThreadUDP;
//	private ClipboardWatchThread mClipThread;
	public List<String> mIpList = Collections.synchronizedList( new ArrayList<String>() );
//	public List<PointerFrame> mPointerList = Collections.synchronizedList( new ArrayList<PointerFrame>() );
	final String CONFIG_FILE = "./config.txt";
	MultiTouchServer thisPtr;
	private Thread mDisconnectThread;
	private String mOSName;
	private String mOSArch;
	private String mOSVersion;

	public MultiTouchServer() throws IOException, AWTException 
	{
		thisPtr = this;
		loadConfig(CONFIG_FILE);
		/*
		try
		{
			localAddr = InetAddress.getLocalHost();
			if (localAddr.isLoopbackAddress())
			{
				localAddr = LinuxInetAddress.getLocalHost();
			}
			mLocalHostName = localAddr.getHostName();
			mLocalHostIP =  localAddr.getHostAddress();
		}
		catch (UnknownHostException e)
		{
			e.printStackTrace();
		}
		*/
	    // ローカルホスト名とIPアドレスを取得
	    try 
	    {
			InetAddress addr = InetAddress.getLocalHost();
			mLocalHostName = addr.getHostName();
			mLocalHostIP   = addr.getHostAddress();
	    }
	    catch (UnknownHostException e) 
	    {
	      e.printStackTrace();
	    }
		mOSName = System.getProperty("os.name");
		mOSArch = System.getProperty("os.arch");
		mOSVersion = System.getProperty("os.version");
		
		// ポートの重複チェック
		try
		{
			ServerSocket serverSoc = new ServerSocket(mPortNum);
			serverSoc.close();
			
		}
		catch( BindException e )
		{
			JOptionPane.showMessageDialog(null, "ポート番号:" + String.valueOf(mPortNum) + "が既に使用されています。\nサーバソフトが起動中でないか確認してください。\n起動していない場合、config.txtのport:の値を変更してください。");
			System.exit(1);
			return;
		}
		
		SystemTray tray = SystemTray.getSystemTray();

		mPopupMenu = new PopupMenu("Wi-Fi Multi TouchPad Server");
		makeMenu( mPopupMenu );
		
		Image iconImage = ImageIO.read(new File("./res/icon.png"));
		icon = new TrayIcon( iconImage, "Wi-Fi Multi TouchPad Server\nServer IP:" + mLocalHostIP, mPopupMenu);
		
		icon.addMouseListener(this);
		
		tray.add(icon);
		
		mReceiveThread = new ReceiveThread( this, mPortNum );
		mReceiveThread.start();
		mReceiveThreadUDP = new ReceiveThreadUDP( this, mPortNum );
		mReceiveThreadUDP.start();
		
		// 切断チェックスレッド
		mDisconnectThread = new Thread(){
			public void run()
			{
				while( true )
				{
					try {
						sleep(10000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
					String disconnectedIp = "";
					synchronized( mIpList )
					{
						for( String ip : mIpList)
						{
							// ipへの接続確認。
							InetSocketAddress socketAddress = new InetSocketAddress(ip, mPortNum );
	
							//socketAddressの値に基づいて通信に使用するソケットを作成する。
	
							Socket socket = new Socket();
							//タイムアウトは10秒(10000msec)
							try {
								socket.connect(socketAddress, 10000);
	
								/*
								//接続先の情報を入れるInetAddress型のinadrを用意する。
								InetAddress inadr;
	
								//inadrにソケットの接続先アドレスを入れ、nullである場合には
								//接続失敗と判断する。
								//nullでなければ、接続確立している。
								if ((inadr = socket.getInetAddress()) != null) 
								{
									System.out.println("Connect to " + inadr);
								}
								else 
								{
									System.out.println("Connection failed.");
									return;
								}
	
								//メッセージの送信処理
	
								//PrintWriter型のwriterに、ソケットの出力ストリームを渡す。
								PrintWriter writer = new PrintWriter(socket.getOutputStream());
	
								//ソケットから出力する。
								writer.println( "Ho_!" );
								//終了処理
								writer.close();
								*/
								socket.close();
								
							} catch (IOException e) {
								// 切断されたと判断。
	//							e.printStackTrace();
								disconnectedIp = ip;
								break;
							}
						}
					}
					if( disconnectedIp.equals(""))
					{
					}
					else
					{
						icon.displayMessage("Disconnected", disconnectedIp, TrayIcon.MessageType.INFO);
						mIpList.remove( mIpList.indexOf( disconnectedIp ) );
						makeMenu( mPopupMenu );
					}
				}//while(true)
			}
		};
		mDisconnectThread.start();
		
		try {
			mDisconnectThread.join();
		} catch (InterruptedException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
		DebugLog( "切断検出スレッドが終了しました。" );
		
		
//		mClipThread = new ClipboardWatchThread( 500, mPortNum );
//		mClipThread.start();
	}

	
	private void loadConfig( String filename )
	{
		try{
			File file = new File(filename);
			BufferedReader br = new BufferedReader(new FileReader(file));

			String str;

			while((str = br.readLine()) != null)
			{
				if( str != null )
				{
					String item[] = str.split(":");
					if( item[0].equals( "port" ) )
					{
						try
						{
							mPortNum = Integer.parseInt(item[1]);
						}
						catch( NumberFormatException e )
						{
							mPortNum = -1;
						}
					}
					else if(item[0].equals( "debug" ) )
					{
						if( item[1].equals( "true" ) )
						{
							mIsDebug = true;
						}
					}
				}
			}
			br.close();
		}
		catch(FileNotFoundException e)
		{
			System.out.println(e);
		}
		catch(IOException e)
		{
			System.out.println(e);
		}
	}
	
	private void saveConfig( String filename )
	{
		try
		{
			File file = new File(filename);
			FileWriter filewriter = new FileWriter(file);
			
			filewriter.write( "port:" + String.valueOf(mPortNum) + "\n" );
			
			filewriter.close();
		}
		catch(IOException e)
		{
			System.out.println(e);
		}
	}
	
	private void makeMenu( PopupMenu menu)
	{
		menu.removeAll();
		
		MenuItem item = new MenuItem(mLocalHostName + "(" + mLocalHostIP + ")" );
		menu.add(item);
		
		item = new MenuItem("Port setting:" + String.valueOf( mPortNum ) );
		item.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent event) {
					int result;

					JSpinner spinner = new JSpinner(new SpinnerNumberModel (mPortNum, 0, 65535, 1) );

					result = JOptionPane.showConfirmDialog(null, spinner,"TCP Port Number", 
								JOptionPane.OK_CANCEL_OPTION, 
								JOptionPane.QUESTION_MESSAGE);

					if (result == JOptionPane.OK_OPTION) {
						int val = (Integer) spinner.getValue(); 
						if( mPortNum != val )
						{
							mPortNum = val;
							mReceiveThread.waitForStop();
							mReceiveThread = new ReceiveThread( thisPtr, mPortNum );
							mReceiveThread.start();
							mReceiveThreadUDP.waitForStop();
							mReceiveThreadUDP = new ReceiveThreadUDP( thisPtr, mPortNum );
							mReceiveThreadUDP.start();
							
							saveConfig( CONFIG_FILE );

							mIpList.clear();
							
							makeMenu( mPopupMenu );
							JOptionPane.showMessageDialog(null, "Set to " + String.valueOf(mPortNum));
						}
					}

//					result = JOptionPane.showInputDialog(null, "Port setting.", String.valueOf(mPortNum));
					/*
					if( result != null )
					{
						int portNum = 0;
						try
						{ 
							portNum = Integer.parseInt(result);
						}
						catch( NumberFormatException e )
						{
							portNum = -1;
						}

						if( ( portNum >= 0 ) && (portNum <= 65535 ) )
						{
							mPortNum = portNum;
							JOptionPane.showMessageDialog(null, result + "に設定しました。コントローラー側のポート番号も合わせて設定してください。");
							makeMenu( mPopupMenu );
						}
						else
						{
							JOptionPane.showMessageDialog(null, "0～65535までの数値で入力してください。", "Error", JOptionPane.ERROR_MESSAGE);
						}
					}
					*/
				}
			});
		menu.add(item);
		
		item = new MenuItem( "Connecting Phones" );
		menu.add(item);
		
		
		if( mIpList.size() == 0 )
		{
			item = new MenuItem("- no connection");
			menu.add(item);
		}
		else
		{
			synchronized( mIpList )
			{
				for( String ip : mIpList )
				{
					item = new MenuItem( ip );
					menu.add( "- " + item.getLabel() );
				}
			}
		}
		
		item = new MenuItem("About");
		item.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent event)
				{
					JOptionPane.showMessageDialog(null, "Wi-Fi Multi TouchPad Server\nVersion " + VERSION );
				}
			});
		menu.add(item);
		
		item = new MenuItem("Visit Web Site");
		item.addActionListener(new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent event)
				{
					mReceiveThread.RunBrowser( "https://sites.google.com/site/wifimultitouchpad/" );
				}
			});
		menu.add(item);
		
		item = new MenuItem("Quit");
		item.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					System.exit(0);
				}
			});
		menu.add(item);
		
		menu.insertSeparator(1);
		menu.insertSeparator(3);
		menu.insertSeparator(menu.getItemCount()-3);
//		menu.insertSeparator(menu.getItemCount()-2);
		menu.insertSeparator(menu.getItemCount()-1);
	}
	
	/* MouseListener 実装 ここから*/
	@Override
	public void mouseClicked(MouseEvent event )
	{
		if (event.getButton() == MouseEvent.BUTTON1)
		{
//			icon.displayMessage("メッセージ", "警告", TrayIcon.MessageType.WARNING);
		}
	}

	@Override
	public void mouseReleased(MouseEvent event)
	{
		// 処理なし。
	}

	@Override
	public void mouseEntered(MouseEvent event)
	{
		// 処理なし。
	}

	@Override
	public void mouseExited(MouseEvent event)
	{
		// 処理なし。
	}

	@Override
	public void mousePressed(MouseEvent event)
	{
		// 処理なし。
	}
	/* MouseListener 実装 ここまで*/
	
	public static void main(String[] args)
	{
		try
		{
			new MultiTouchServer();
		}
		catch (IOException ex)
		{
			ex.printStackTrace();
		}
		catch (AWTException ex)
		{
			ex.printStackTrace();
		}
		
		int i = 0;
	}

	public boolean noticeIpAddress( String ipaddress )
	{
		synchronized( mIpList )
		{
			for( String ip : mIpList)
			{
				if( ip.equals( ipaddress ))
				{
					return false;
				}
			}
		}

		icon.displayMessage("Connected", ipaddress, TrayIcon.MessageType.INFO);
		
		mIpList.add( ipaddress );
		makeMenu( mPopupMenu );
		
		sendHostInfo( ipaddress );
		
		return true;
	}
	
	public void showConnected( String ipaddress )
	{
		icon.displayMessage("Connected", ipaddress, TrayIcon.MessageType.INFO);
		sendHostInfo( ipaddress );
	}
	
	public void sendHostInfo( String ipaddress )
	{
		// 接続元へ、ホスト情報を送信
		String sendStr = null;
		try {
			
			if( mIsDebug == true )
			{
				sendStr = URLEncoder.encode(mLocalHostName, "UTF-8") + "," + 
						  URLEncoder.encode("Debug" + mOSName, "UTF-8") + ","	+ 
						  URLEncoder.encode(mOSArch, "UTF-8") + "," + 
						  URLEncoder.encode(mOSVersion, "UTF-8") + "," +
						  URLEncoder.encode(VERSION, "UTF-8");
			}
			else
			{
				sendStr = URLEncoder.encode(mLocalHostName, "UTF-8") + "," + 
						  URLEncoder.encode("MultiPointerServer " + mOSName, "UTF-8") + ","	+ 
						  URLEncoder.encode(mOSArch, "UTF-8") + "," + 
						  URLEncoder.encode(mOSVersion, "UTF-8") + "," +
						  URLEncoder.encode(VERSION, "UTF-8");
			}
			
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		SendThread sThread = new SendThread( ipaddress, mPortNum, "/host_info:" + sendStr + " " );
		sThread.start();
	}
	public void DebugLog( String msg )
	{
		if( mIsDebug == true )
		{
			System.out.println( msg );
		}
	}
}
