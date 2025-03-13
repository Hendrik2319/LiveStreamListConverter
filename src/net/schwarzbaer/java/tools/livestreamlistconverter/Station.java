package net.schwarzbaer.java.tools.livestreamlistconverter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
		if (obj instanceof InputStream input) return readFromInputStream(input);
		
		System.err.printf("Unknown content type of station list: %s%n", obj.getClass().getCanonicalName());
		return null;
	}

	private static String readFromInputStream(InputStream input)
	{
		try (BufferedReader in = new BufferedReader(new InputStreamReader(input)))
		{
			return in.lines().reduce("", (content, line) -> "%s%s%n".formatted(content, line));
		}
		catch (IOException ex)
		{
			System.err.printf("IOException while reading response from station: %s%n", ex.getMessage());
			//ex.printStackTrace();
		}
		return null;
	}
}