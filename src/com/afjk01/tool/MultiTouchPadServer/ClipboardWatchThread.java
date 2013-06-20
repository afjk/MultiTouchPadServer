package com.afjk01.tool.MultiTouchPadServer;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import com.afjk01.tool.MultiTouchPadServer.SendThread;

public class ClipboardWatchThread extends Thread 
{
	private int mSleep;
	private int mPortNum;
	private String mPreClipboad;
	private List<String> mIpList = new ArrayList<String>();
	static private Object mObj;	// 排他制御用。
	private boolean mHalt;
	
	public ClipboardWatchThread( int sleep, int port )
	{
		mSleep = sleep;
		mPortNum = port;
		
		mPreClipboad = getClipboad();
	}
	@Override
	public void run()
	{
		while(!mHalt)
		{
			try
			{
				sleep(mSleep);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
			
			String str = getClipboad();
			
			if( str.equals( mPreClipboad ))
			{
			}
			else
			{
				// 一致しなければ、通知。
				mPreClipboad = str;
				String sendStr;
				try
				{
					synchronized( mIpList )
					{
						
						for( String ipaddress : mIpList )
						{
							sendStr = URLEncoder.encode(str, "UTF-8");
							SendThread sThread = new SendThread( ipaddress, mPortNum, "/clipboard:" + sendStr + " " );
							sThread.start();
						}
					}
				}
				catch (UnsupportedEncodingException e)
				{
					e.printStackTrace();
				}
			}
		}
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
//		  e.printStackTrace();
		}
		catch (IOException e)
		{
//		  e.printStackTrace();
		}
		
		return str;
	}
	
	public void setIpList( List<String > ipList )
	{
		mIpList = ipList;
	}
	
	public void setPortNum( int port )
	{
		mPortNum = port;
	}
    public void halt() 
    {
        mHalt = true;
        interrupt();
    }

}
