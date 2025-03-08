package net.schwarzbaer.java.tools.livestreamlistconverter;

class StreamAdress
{
	String url;
	String name;
	String genre;
	String country;
	int bitRate;
	int isFavorite;

	StreamAdress(String name, String url)
	{
		this.url = url;
		this.name = name;
		this.genre = "";
		this.country = "";
		this.bitRate = 0;
		this.isFavorite = 1;
	}

	@Override
	public String toString()
	{
		return "StreamAdress [ name=\"" + name + "\", url=\"" + url + "\" ]";
	}
}