package net.schwarzbaer.java.tools.livestreamlistconverter;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

enum SourceType
{
	plain(SourceType::parseLine_plain),
	pls  (SourceType::parseLine_pls  ),
	;
	final LineParser parser;
	
	SourceType(LineParser parser)
	{
		this.parser = Objects.requireNonNull(parser);
	}
	
	static SourceType parseSourceType(String str)
	{
		try
		{
			return valueOf(str);
		}
		catch (Exception ex)
		{
			System.err.printf("Found unknown SourceType: \"%s\"%n", str);
			return null;
		}
	}

	interface LineParser
	{
		StreamAdress parseLine(String line, String label);
	}
	
	private static StreamAdress parseLine_pls(String line, String label)
	{
		if (line.startsWith("File")) {
			int pos = line.indexOf('=');
			if (pos>=0)
				return new StreamAdress(
						label,
						line.substring(pos+1)
				);
		}
		return null;
	}

	private static StreamAdress parseLine_plain(String line, String label)
	{
		if (isURL(line))
			return new StreamAdress(
					label,
					line
			);
		return null;
	}

	private static boolean isURL(String url)
	{
		try
		{
			new URI(url).toURL();
			return true;
		}
		catch (URISyntaxException e) { return false; }
		catch (MalformedURLException e) { return false; }
	}
}
