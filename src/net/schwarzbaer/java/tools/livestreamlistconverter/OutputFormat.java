package net.schwarzbaer.java.tools.livestreamlistconverter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Vector;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

abstract class OutputFormat
{
	enum FormatEnum
	{
		PLSPlayList         (PLSPlayList         ::new),
		M3UPlayList         (M3UPlayList         ::new),
		ETS2RadioList       (ETS2RadioList       ::new),
		StarTruckerRadioList(StarTruckerRadioList::new),
		;
		final Supplier<OutputFormat> create;
		private FormatEnum(Supplier<OutputFormat> create)
		{
			this.create = create;
		}
	}
	
	static Map<FormatEnum, OutputFormat> createFormats()
	{
		Map<FormatEnum, OutputFormat> map = new EnumMap<>(FormatEnum.class);
		for (FormatEnum fe : FormatEnum.values())
			map.put(fe, fe.create.get());
		return map;
	}
	
	final String fileLabel;
	final String fileTypeName;
	final String fileTypeExt;
	
	protected OutputFormat(String fileLabel, String fileTypeName, String fileTypeExt)
	{
		this.fileLabel = fileLabel;
		this.fileTypeName = fileTypeName;
		this.fileTypeExt = fileTypeExt;
	}

	abstract String createOutputFileContent(Vector<StreamAdress> adressList, IntConsumer setProgress);
	
	static class ETS2RadioList extends OutputFormat
	{
		ETS2RadioList()
		{
			super("ETS2 Radio List", "SII file","sii");
		}
		
		@Override
		String createOutputFileContent(Vector<StreamAdress> adressList, IntConsumer setProgress)
		{
			StringBuilder sb = new StringBuilder();
			sb.append("SiiNunit\r\n");
			sb.append("{\r\n");
			sb.append("live_stream_def : _nameless.35BF.92E8 {\r\n");
			for (int i=0; i<adressList.size(); i++) {
				StreamAdress adress = adressList.get(i);
				//sb.append(String.format("stream_data[]: \"%s|%s\"\r\n", adress.url,adress.name));
				sb.append(String.format("stream_data[]: \"%s|%s|%s|%s|%d|%d\"\r\n", adress.url,adress.name,adress.genre,adress.country,adress.bitRate,adress.isFavorite));
				//stream_data[32]: "http://striiming.trio.ee/uuno.mp3|Raadio Uuno|Rock|EST|128|0"
				setProgress.accept(i+1);
			}
			sb.append("}\r\n");
			sb.append("}\r\n");
			return sb.toString();
		}
	}
	
	static class StarTruckerRadioList extends OutputFormat
	{
		StarTruckerRadioList()
		{
			super("StarTrucker Radio List", "Text file","txt");
		}
		
		@Override
		String createOutputFileContent(Vector<StreamAdress> adressList, IntConsumer setProgress)
		{
			StringBuilder sb = new StringBuilder();
			sb.append("### Add a custom station on a new line using the following format:\r\n");
			sb.append("### [url]|[name]|[genre]\r\n");
			sb.append("http://stream.simulatorradio.com:8002/stream.mp3|SimulatorRadio|Sim radio\r\n");
			sb.append("http://radio.trucksim.fm:8000/stream|TruckSimFM|Sim radio\r\n");
			sb.append("https://oreo.truckstopradio.co.uk/radio/8000/radio.mp3|TruckStopRadio|Sim radio\r\n");
			sb.append("https://radio.truckers.fm|TruckersFM|Sim radio\r\n");
			sb.append("\r\n");
			for (int i=0; i<adressList.size(); i++) {
				StreamAdress adress = adressList.get(i);
				sb.append("%s|%s|Custom radio\r\n".formatted(adress.url, adress.name));
				setProgress.accept(i+1);
			}
			return sb.toString();
		}
	}
	
	static class PLSPlayList extends OutputFormat
	{
		PLSPlayList()
		{
			super("PLS PlayList", "PLS file","pls");
		}
		
		@Override
		String createOutputFileContent(Vector<StreamAdress> adressList, IntConsumer setProgress)
		{
			StringBuilder sb = new StringBuilder();
			sb.append("[playlist]").append("\r\n");
			sb.append("numberofentries=").append(adressList.size()).append("\r\n");
			for (int i=0; i<adressList.size(); i++) {
				StreamAdress adress = adressList.get(i);
				sb.append(String.format("File%d=%s\r\n", i+1,adress.url));
				sb.append(String.format("Title%d=%s\r\n", i+1,adress.name));
				sb.append(String.format("Length%d=-1\r\n", i+1));
				setProgress.accept(i+1);
			}
			sb.append("Version=2").append("\r\n");
			return sb.toString();
		}
	}
	
	static class M3UPlayList extends OutputFormat
	{
		M3UPlayList()
		{
			super("M3U PlayList", "M3U file","m3u");
		}
		
		@Override
		String createOutputFileContent(Vector<StreamAdress> adressList, IntConsumer setProgress)
		{
			StringBuilder sb = new StringBuilder();
			sb.append(String.format("#EXTM3U%n"));
			for (int i=0; i<adressList.size(); i++) {
				StreamAdress adress = adressList.get(i);
				sb.append(String.format("#EXTINF:0,%s%n", adress.name));
				sb.append(String.format("%s%n", adress.url));
				setProgress.accept(i+1);
			}
			return sb.toString();
		}
	}
}
