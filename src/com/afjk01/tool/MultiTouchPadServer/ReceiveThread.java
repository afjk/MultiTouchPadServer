package com.afjk01.tool.MultiTouchPadServer;

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Robot;
import java.awt.Toolkit;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;

public class ReceiveThread extends Thread
{
	private InputStream is;
	private OutputStream os;
	private String mIP = "";
	private Point mMemPos;
	private boolean mDragScroll = false;
	private int mDownX = 0;
	private int mDownY = 0;
	private String mOSName;
	private String mOSArch;
	private String mOSVersion;
	private String mUserName;
	private String mHostName;
	private Robot rb;	// マウス操作用。
//	Toolkit tk = getToolkit();
	private Dimension mDim;
	private int mPortNum;
	private boolean mChangingApp;
	private boolean mChangingWin;
	private ClipboardWatchThread mClipThread;
	private MultiTouchServer mMultiTouchServer;
	private boolean mGlassOpening;
	private boolean mHalt;
	private ServerSocket mServerSoc;
	
	public ReceiveThread( MultiTouchServer server, int port )
	{
		mMultiTouchServer = server;
		mPortNum = port;
		mHalt = false;
		
		// robot生成
		try {
			rb=new Robot();
			rb.setAutoDelay(0);
		}
		catch (AWTException e) 
		{
			e.printStackTrace();
		}
//		mDim = tk.getScreenSize();
	}

    public void halt() 
    {
		try {
			if( mServerSoc != null )
			{
				mServerSoc.close();
			}
		} catch (IOException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
        mHalt = true;
        interrupt();
    }

	public void waitForStop() 
	{
		halt();
		// スレッドが終わるのを待つ
		while (isAlive())
		{
			System.out.println("Waiting End Receive Thread" );
		}
	}
	public void run()
	{
		// システム情報取得
		String str = System.getProperty("os.name");
		mOSName = str;
		str = System.getProperty("os.arch");
		mOSArch = str;
		str = System.getProperty("os.version");
		mOSVersion = str;
		str = System.getProperty("user.name");
		mUserName = str;
		
		DebugLog("user name:" + str);
		DebugLog("os name:" + str);
		DebugLog("os arch:" + str);
		DebugLog("os version:" + str);
		
		try
		{
			InetAddress addr = InetAddress.getLocalHost();
			mHostName = addr.getHostName();

			DebugLog("host name:" + addr.getHostName());
			DebugLog("host ip:" + addr.getHostAddress());
		}
		catch (Exception e) 
		{
			e.printStackTrace();
		}

		// クリップボード監視の開始。
		mClipThread = new ClipboardWatchThread( 500, mPortNum );
		mClipThread.start();
		
		try {
			// ポート番号は、30000
			//ソケットを作成
			mServerSoc = new ServerSocket(mPortNum);
		
			//クライアントからの接続を待機するaccept()メソッド。
			//accept()は、接続があるまで処理はブロックされる。
			//もし、複数のクライアントからの接続を受け付けるようにするには
			//スレッドを使う。
			//accept()は接続時に新たなsocketを返す。これを使って通信を行なう。
			String rcvMsg = null;
			Socket socket=null;
			BufferedReader reader = null;

			while(!mHalt)
			{
				DebugLog("Waiting for Connection. ");

				socket = mServerSoc.accept();
				//接続があれば次の命令に移る。
				DebugLog("Connect to " + socket.getInetAddress());

				mIP = socket.getInetAddress().toString();

				if( mIP.charAt(0) == '/' )
				{
					mIP = mIP.substring( 1, mIP.length());
				}
//				mPointTouchServer.setIpAddress( mIP );
				boolean addIp = mMultiTouchServer.noticeIpAddress( mIP );
				if( addIp == true )
				{
					mClipThread.setIpList( mMultiTouchServer.mIpList );
				}
				
				//socketからのデータはInputStreamReaderに送り、さらに
				//BufferedReaderによってバッファリングする。
				reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

				//読み取ったデータを表示する。
				try
				{
					rcvMsg = reader.readLine();
				}
				catch( SocketException e )
				{
					System.out.println("Socket Exceptoin!");
					e.printStackTrace();
				}
				
				DebugLog("Message from client :" + rcvMsg);

				//System.out.println( rcvMsg );
				
				//入力ストリームをクローズする。
				reader.close();
				reader = null;
				//通信用ソケットの接続をクローズする。
				socket.close();
				socket = null;
				//待ち受け用ソケットをクローズする。
				if(rcvMsg.matches("end"))
				{
					mServerSoc.close();
					mHalt = true;
					System.out.println("Stopped.");
				}

				char wk = ' ';
				String command = "";
				for( int i = 0; i< rcvMsg.length(); ++i )
				{
					wk = rcvMsg.charAt(i);
					if( wk == ' ' )
					{
						// コマンドの区切り文字。
						// コマンド実行。
						ParthCommand(command);
						command = "";
					}
					else
					{
						command += wk;
					}
				}
			}
			mServerSoc.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		
		mClipThread.halt();
	}// end of run()

	private void ParthCommand(String command) 
	{
		if( command.charAt( 0) != '/' )
		{
			return;
		}
		String com = "";
		ArrayList<String> args = null;
		for( int i=0 ; i < command.length() ;i++ )
		{
			char wk = command.charAt( i );
			
			if( wk == ':' )
			{
				// 命令部取得終了。
				args = GetArgs( command.substring(i+1,command.length()) );
				break;
			}
			else
			{
				com += wk;
			}
		}
		ExecCommand( com, args );
	}
	
	private ArrayList<String> GetArgs(String argstr) 
	{
		ArrayList<String> args = new ArrayList<String>();
		
		char wk = ' ';
		String wkStr = "";
		for( int i = 0; i< argstr.length(); i++ )
		{
			wk = argstr.charAt( i );
			if( wk == ',' )
			{
				args.add( wkStr);
				wkStr = "";
			}
			else
			{
				wkStr += wk;
			}
		}
		args.add( wkStr );
		return args;
	}
	
	private void ExecCommand(String com, ArrayList<String> args) 
	{
		if( com.equals("/mouse") )
		{
			int x = Integer.parseInt( args.get(0) );
			int y = Integer.parseInt( args.get(1) );
			
			PointerInfo pointerInfo = MouseInfo.getPointerInfo();
			Point pos = pointerInfo.getLocation();

			x = pos.x + x;
			y = pos.y + y;
			rb.mouseMove( x, y );
			
//			if( x > mDim.width )
			{
//				SendThread sThread = new SendThread( mIP, mPortNum, "/rightover: " );
//				sThread.start();
			}
//			else if( x < 0 )
			{
//				SendThread sThread = new SendThread( mIP, mPortNum, "/leftover: " );
//				sThread.start();
			}
		}
		else if( com.equals("/tap") )
		{
			rb.mousePress(  InputEvent.BUTTON1_MASK );
			rb.mouseRelease(InputEvent.BUTTON1_MASK);
		}
		else if( com.equals("/doubletap") )
		{
			rb.mousePress(  InputEvent.BUTTON1_MASK );
			rb.mouseRelease(InputEvent.BUTTON1_MASK);
			rb.mousePress(  InputEvent.BUTTON1_MASK );
			rb.mouseRelease(InputEvent.BUTTON1_MASK);
		}
		else if( com.equals( "/twotap" ) )
		{
			rb.mousePress(  InputEvent.BUTTON3_MASK );
			rb.mouseRelease(InputEvent.BUTTON3_MASK);
		}
		else if( com.equals( "/pressbutton1" ) )
		{
			rb.mousePress(  InputEvent.BUTTON1_MASK );
		}
		else if( com.equals( "/releasebutton1" ) )
		{
			rb.mouseRelease(  InputEvent.BUTTON1_MASK );
		}
		else if( com.equals( "/pressbutton3" ) )
		{
			rb.mousePress(  InputEvent.BUTTON3_MASK );
		}
		else if( com.equals( "/releasebutton3" ) )
		{
			rb.mouseRelease(InputEvent.BUTTON3_MASK);
		}
		else if( com.equals("/wheel") )
		{
			int wheelAmt = Integer.parseInt( args.get(0) )/5;
			rb.mouseWheel( wheelAmt );
		}
		else if( com.equals("/text"))
		{
			// テキストをクリップボードに保存してCtrl+Vで出力。
			
			String str = args.get(0);
			try {
				str = URLDecoder.decode(str, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}
			
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			StringSelection selection = new StringSelection(str);
			clipboard.setContents(selection, null);
			rb.keyPress(KeyEvent.VK_CONTROL );
			rb.keyPress(KeyEvent.VK_V );
			rb.keyRelease(KeyEvent.VK_CONTROL );
			rb.keyRelease(KeyEvent.VK_V );
			
		}
		else if( com.equals("/clipboard"))
		{
			String str = args.get(0);
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			StringSelection selection = new StringSelection(str);
			clipboard.setContents(selection, null);
		}
		else if( com.equals("/mempos"))
		{
			PointerInfo pointerInfo = MouseInfo.getPointerInfo();
			mMemPos = pointerInfo.getLocation();
		}
		else if( com.equals("/remempos"))
		{
			rb.mouseMove( mMemPos.x, mMemPos.y );
		}
		else if( com.equals("/copy"))
		{
			rb.keyPress(KeyEvent.VK_CONTROL );
			rb.keyPress(KeyEvent.VK_C );
			rb.keyRelease(KeyEvent.VK_CONTROL );
			rb.keyRelease(KeyEvent.VK_C );
		}
		else if( com.equals("/browser"))
		{
//			Desktop desktop = Desktop.getDesktop();

			try {
				String str = getClipboad();
				
				String result = URLEncoder.encode(str, "UTF-8");
				
				RunBrowser( "http://www.google.co.jp/search?&q=" + result );
				
//				desktop.browse(new URI("http://www.google.co.jp/search?&q=" + result ));
			} catch (IOException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}
/*			catch (URISyntaxException e) 
			{
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			};
*/
		}
		else if( com.equals("/next"))
		{
			rb.keyPress(KeyEvent.VK_ALT );
			rb.keyPress(KeyEvent.VK_RIGHT );
			rb.keyRelease(KeyEvent.VK_ALT );
			rb.keyRelease(KeyEvent.VK_RIGHT );
		}
		else if( com.equals("/back"))
		{
			rb.keyPress(KeyEvent.VK_ALT );
			rb.keyPress(KeyEvent.VK_LEFT );
			rb.keyRelease(KeyEvent.VK_ALT );
			rb.keyRelease(KeyEvent.VK_LEFT );
		}
		else if( com.equals("/windowmax"))
		{
			rb.keyPress(KeyEvent.VK_ALT );
			rb.keyPress(KeyEvent.VK_SPACE );
			rb.keyRelease(KeyEvent.VK_ALT );
			rb.keyRelease(KeyEvent.VK_SPACE );
			try {
				sleep(100);
			} catch (InterruptedException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}
			rb.keyPress(KeyEvent.VK_X );
			rb.keyRelease(KeyEvent.VK_X );
		}
		else if( com.equals("/windownormal"))
		{
			rb.keyPress(KeyEvent.VK_ALT );
			rb.keyPress(KeyEvent.VK_SPACE );
			rb.keyRelease(KeyEvent.VK_ALT );
			rb.keyRelease(KeyEvent.VK_SPACE );
			try {
				sleep(100);
			} catch (InterruptedException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}
			rb.keyPress(KeyEvent.VK_R );
			rb.keyRelease(KeyEvent.VK_R );
		}
		else if( com.equals("/windowmin"))
		{
			rb.keyPress(KeyEvent.VK_ALT );
			rb.keyPress(KeyEvent.VK_SPACE );
			rb.keyRelease(KeyEvent.VK_ALT );
			rb.keyRelease(KeyEvent.VK_SPACE );
			try {
				sleep(100);
			} catch (InterruptedException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}
			rb.keyPress(KeyEvent.VK_N );
			rb.keyRelease(KeyEvent.VK_N );
		}
		else if( com.equals("/desktop"))
		{
			rb.keyPress(KeyEvent.VK_WINDOWS );
			rb.keyPress(KeyEvent.VK_D );
			rb.keyRelease(KeyEvent.VK_WINDOWS );
			rb.keyRelease(KeyEvent.VK_D );
		}
		else if( com.equals("/nextapp"))
		{
			if( mChangingApp == false )
			{
				mChangingApp = true;
				rb.keyPress(KeyEvent.VK_WINDOWS );
			}
			rb.keyPress(KeyEvent.VK_TAB );
			rb.keyRelease(KeyEvent.VK_TAB );
		}
		else if( com.equals("/backapp"))
		{
			if( mChangingApp == false )
			{
				mChangingApp = true;
				rb.keyPress(KeyEvent.VK_WINDOWS );
			}
			rb.keyPress(KeyEvent.VK_SHIFT );
			rb.keyPress(KeyEvent.VK_TAB );
			rb.keyRelease(KeyEvent.VK_SHIFT );
			rb.keyRelease(KeyEvent.VK_TAB );
		}
		else if( com.equals("/endchangeapp"))
		{
			if( mChangingApp == true )
			{
				mChangingApp = false;
				rb.keyRelease(KeyEvent.VK_WINDOWS );
			}
		}
		
		else if( com.equals("/nextwin"))
		{
			if( mChangingWin == false )
			{
				mChangingWin = true;
				rb.keyPress(KeyEvent.VK_ALT );
			}
			rb.keyPress(KeyEvent.VK_TAB );
			rb.keyRelease(KeyEvent.VK_TAB );
		}
		else if( com.equals("/backwin"))
		{
			if( mChangingWin == false )
			{
				mChangingWin = true;
				rb.keyPress(KeyEvent.VK_ALT );
				rb.keyPress(KeyEvent.VK_TAB );
				rb.keyRelease(KeyEvent.VK_TAB );
			}
			else
			{
				rb.keyPress(KeyEvent.VK_SHIFT );
				rb.keyPress(KeyEvent.VK_TAB );
				rb.keyRelease(KeyEvent.VK_SHIFT );
				rb.keyRelease(KeyEvent.VK_TAB );
			}
		}
		else if( com.equals("/endchangewin"))
		{
			if( mChangingWin == true )
			{
				mChangingWin = false;
				rb.keyRelease(KeyEvent.VK_ALT );
			}
		}
		
		else if( com.equals("/endchangeapp"))
		{
			if( mChangingApp == true )
			{
				mChangingApp = false;
				rb.keyRelease(KeyEvent.VK_WINDOWS );
			}
		}
		else if( com.equals("/scale"))
		{
			int wheelAmt = Integer.parseInt( args.get(0) );
			if( wheelAmt < 0 )
			{
				wheelAmt = 1;
			}
			else
			{
				wheelAmt = -1;
			}
			
			rb.keyPress(KeyEvent.VK_CONTROL );
			rb.mouseWheel( wheelAmt );
			rb.keyRelease(KeyEvent.VK_CONTROL );
		}
		else if( com.equals("/dragscroll"))
		{
			if( mDragScroll  == false )
			{
				mDragScroll = true;
				rb.mousePress(  InputEvent.BUTTON2_MASK );
				PointerInfo pointerInfo = MouseInfo.getPointerInfo();
				Point pos = pointerInfo.getLocation();
				mDownX = pos.x;
				mDownY = pos.y;
			}
			
			int x = Integer.parseInt( args.get(0) );
			int y = Integer.parseInt( args.get(1) );
			
			x = mDownX + x*10;
			y = mDownY + y*10;
			
			rb.mouseMove( x, y );
		}
		else if( com.equals("/enddragscroll"))
		{
			if( mDragScroll == true )
			{
				rb.mouseRelease(InputEvent.BUTTON2_MASK);
				mDragScroll = false;
			}
		}
		else if( com.equals("/gethostname"))
		{
			/*
			try {
				String sendStr = URLEncoder.encode(mHostName, "UTF-8");
				SendThread sThread = new SendThread( mIP, mPortNum, "/hostname:" + sendStr + " " );
				sThread.start();
				
			} catch (UnsupportedEncodingException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}
			
			// 暫定
			mPointTouchServer.addIpAddress( mIP );
//			mPointTouchServer.setIpList( mIpList );
			mClipThread.setIpList( mPointTouchServer.mIpList );
			*/
		}
		else if( com.equals("/connect"))
		{
			// 接続要求。
			// 接続クライアント一覧に追加。
//			addIpAddress( mIP );
//			mPointTouchServer.setIpList( mIpList );
//			mClipThread.setIpList( mIpList );
			mMultiTouchServer.showConnected( mIP );
		}
		else if( com.equals("/openglass"))
		{
			if( mGlassOpening == false )
			{
				rb.keyPress( KeyEvent.VK_WINDOWS );
				rb.keyPress( 107 );
				rb.keyRelease(KeyEvent.VK_WINDOWS );
				rb.keyRelease( 107 );
				mGlassOpening = true;
			}
		}
		else if( com.equals("/closeglass"))
		{
			if( mGlassOpening == true )
			{
				rb.keyPress( KeyEvent.VK_WINDOWS );
				rb.keyPress( KeyEvent.VK_ESCAPE );
				rb.keyRelease(KeyEvent.VK_WINDOWS );
				rb.keyRelease( KeyEvent.VK_ESCAPE );
				mGlassOpening = false;
			}
		}
		else if( com.equals("/keypress"))
		{
			int keycode = Integer.parseInt( args.get(0) );
			rb.keyPress( keycode );
		}
		else if( com.equals("/keyrelease"))
		{
			int keycode = Integer.parseInt( args.get(0) );
			rb.keyRelease( keycode );
		}
		
		args.clear();
		args = null;
	}
	
	private String getClipboad()
	{
		String str = "";
		
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		Transferable object = clipboard.getContents(null);
		
		try 
		{
		  str = (String)object.getTransferData(DataFlavor.stringFlavor);
		}
		catch(UnsupportedFlavorException e)
		{
		  e.printStackTrace();
		}
		catch (IOException e)
		{
		  e.printStackTrace();
		}
		
		return str;
	}

	public void setPortNum( int port )
	{
//		synchronized( mObj )
		{
			mPortNum = port;
		}
	}

	public void RunBrowser(String url ) 
	{
		Desktop desktop = Desktop.getDesktop();
		
		try
		{
			desktop.browse(new URI( url ));
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		catch (URISyntaxException e)
		{
			e.printStackTrace();
		}
	}
	
	public void DebugLog( String msg )
	{
		if( mMultiTouchServer.mIsDebug == true )
		{
			System.out.println( msg );
		}
	}

	/*
	private void addIpAddress( String ipaddress )
	{
		for( String ip : mIpList)
		{
			if( ip.equals( ipaddress ))
			{
				return;
			}
		}
		
		mIpList.add( ipaddress );
	}
	*/
}// end of testThread class
