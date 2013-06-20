package com.afjk01.tool.MultiTouchPadServer;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class SendThread extends Thread
{
	private String mIP;
	private String mMessage;
	private int mPortNum;
	
	public SendThread( String ip, int port, String message )
	{
		mIP = ip;
		mPortNum = port;
		mMessage = message;
	}
	
	@Override
	public void run()
	{
		if( mIP  == "" )
		{
			return;
		}
		
		try 
		{
			String ipAddress = mIP;
			if( ipAddress.charAt(0) == '/' )
			{
				ipAddress = ipAddress.substring( 1, ipAddress.length());
			}
			
			InetSocketAddress socketAddress = new InetSocketAddress(ipAddress, mPortNum );

			//socketAddressの値に基づいて通信に使用するソケットを作成する。

			Socket socket = new Socket();
			//タイムアウトは10秒(10000msec)
			socket.connect(socketAddress, 10000);

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
			writer.println( mMessage );
			//終了処理
			writer.close();
			socket.close();
		}
		catch( SocketTimeoutException e)
		{
			System.out.print( "SocketTimeoutException" );
			System.out.print( mIP );
			e.printStackTrace();
		}
		catch (IOException e) 
		{
			e.printStackTrace();
		}
	}
}
