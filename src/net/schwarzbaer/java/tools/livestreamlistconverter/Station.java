package net.schwarzbaer.java.tools.livestreamlistconverter;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Vector;

class Station
{
	
	String url = null;
	String name = null;
	SourceType type = null;
	String stationResponse = null;

	@Override
	public String toString()
	{
		return "Station [ name=\"" + name + "\", url=\"" + url + "\", type=\"" + type + "\" ]";
	}
	
	Vector<StreamAdress> readStreamAdressesFromWeb()
	{
		if (url==null) return null;
		stationResponse = getContent(url);
		if (stationResponse==null) return null;
		
		Vector<StreamAdress> adresses = new Vector<>();
		stationResponse.lines().forEach(line -> {
			String label = String.format("%s(%d)", name, adresses.size()+1);
			StreamAdress streamAdress = type==null ? null : type.parser.parseLine(line, label);
			if (streamAdress!=null)
				adresses.add(streamAdress);
		});
		
		if (adresses.size()==1) adresses.get(0).name = name;
		return adresses;
	}

	private static String getContent(String url)
	{
		Object obj; 
		try { obj = new URI(url).toURL().getContent(); }
		catch (URISyntaxException e) { e.printStackTrace(); return null; }
		catch (MalformedURLException e) { e.printStackTrace(); return null; }
		catch (IOException e) { e.printStackTrace(); return null; }
		
		if (obj==null) return null;
		if (obj instanceof String str) return str;
		if (obj instanceof InputStream input)
		{
			StringBuilder sb = new StringBuilder();
			byte[] buffer = new byte[10000];
			try
			{
				int len;
				while( (len=input.read(buffer))>=0 )
					sb.append(new String(buffer,0,len));
			}
			catch (IOException e) {}
			try { input.close(); } catch (IOException e) {}
			return sb.toString();
		}
		System.out.println("Unknown content type of station list: "+obj.getClass().getCanonicalName());
		return null;
	}
}