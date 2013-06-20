package com.afjk01.tool.MultiTouchPadServer;

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
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
import java.util.Date;

public class ReceiveThreadUDP extends Thread
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
//	Toolkit tk = getToolkit();
	private Dimension mDim;
	private int mPortNum;
	private boolean mChangingApp;
	private boolean mChangingWin;
	private MultiTouchServer mMultiTouchServer;
	private boolean mGlassOpening;
	private boolean mHalt;
	private DatagramSocket mDgSocket;
	
	public ReceiveThreadUDP( MultiTouchServer server, int port )
	{
		mMultiTouchServer = server;
		mPortNum = port;
		mHalt = false;
	}

    public void halt() 
    {
    	
    	mDgSocket.close();
    	
        mHalt = true;
        interrupt();
    }

	public void waitForStop() 
	{
		halt();
		// スレッドが終わるのを待つ
		while (isAlive())
		{
			System.out.println("Waiting End Receive UDP Thread" );
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
		
		try {
	        mDgSocket = new DatagramSocket(mPortNum);

	        byte buffer[] = new byte[1024];
	        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

	        while (!mHalt) {
	            mDgSocket.receive(packet);

	            DebugLog(new String(packet.getData(), 
	                            0, packet.getLength()));
	            DebugLog( ": " + new Date() );
	            DebugLog( packet.getAddress().toString() );
	        	
				mIP = packet.getAddress().toString();

				if( mIP.charAt(0) == '/' )
				{
					mIP = mIP.substring( 1, mIP.length());
				}
				
				SendThread sThread = new SendThread( mIP, mPortNum, "/hostname:" + mHostName + " " );
				sThread.start();
	        }
	        /*
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
			*/
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		
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
		}
		else if( com.equals("/tap") )
		{
		}
		
		args.clear();
		args = null;
	}
	
	public void setPortNum( int port )
	{
		{
			mPortNum = port;
		}
	}

	
	public void DebugLog( String msg )
	{
		if( mMultiTouchServer.mIsDebug == true )
		{
			System.out.println( msg );
		}
	}

}// end of testThread class
