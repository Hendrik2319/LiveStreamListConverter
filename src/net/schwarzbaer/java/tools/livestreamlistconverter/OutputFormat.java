package net.schwarzbaer.java.tools.livestreamlistconverter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Vector;
import java.util.function.Supplier;

import net.schwarzbaer.java.lib.gui.ProgressDialog;

abstract class OutputFormat
{
	enum FormatEnum
	{
		ETS2RadioList(ETS2RadioList::new),
		PLSPlayList  (PLSPlayList  ::new),
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

	abstract String createOutputFileContent(ProgressDialog pd, Vector<StreamAdress> adressList);
	
	static class ETS2RadioList extends OutputFormat
	{
		ETS2RadioList()
		{
			super("ETS2 Radio List", "SII file(*.sii)","sii");
		}
		
		@Override
		String createOutputFileContent(ProgressDialog pd, Vector<StreamAdress> adressList)
		{
			pd.setValue(0, adressList.size());
			StringBuilder sb = new StringBuilder();
			sb.append("SiiNunit\r\n");
			sb.append("{\r\n");
			sb.append("live_stream_def : _nameless.35BF.92E8 {\r\n");
			for (int i=0; i<adressList.size(); i++) {
				StreamAdress adress = adressList.get(i);
				//sb.append(String.format("stream_data[]: \"%s|%s\"\r\n", adress.url,adress.name));
				sb.append(String.format("stream_data[]: \"%s|%s|%s|%s|%d|%d\"\r\n", adress.url,adress.name,adress.genre,adress.country,adress.bitRate,adress.isFavorite));
				//stream_data[32]: "http://striiming.trio.ee/uuno.mp3|Raadio Uuno|Rock|EST|128|0"
				pd.setValue(i+1);
			}
			sb.append("}\r\n");
			sb.append("}\r\n");
			return sb.toString();
		}
	}
	
	static class PLSPlayList extends OutputFormat
	{
		PLSPlayList()
		{
			super("PLS PlayList", "PLS file(*.pls)","pls");
		}
		
		@Override
		String createOutputFileContent(ProgressDialog pd, Vector<StreamAdress> adressList)
		{
			pd.setValue(0, adressList.size());
			StringBuilder sb = new StringBuilder();
			sb.append("[playlist]").append("\r\n");
			sb.append("numberofentries=").append(adressList.size()).append("\r\n");
			for (int i=0; i<adressList.size(); i++) {
				StreamAdress adress = adressList.get(i);
				sb.append(String.format("File%d=%s\r\n", i+1,adress.url));
				sb.append(String.format("Title%d=%s\r\n", i+1,adress.name));
				sb.append(String.format("Length%d=-1\r\n", i+1));
				pd.setValue(i+1);
			}
			sb.append("Version=2").append("\r\n");
			return sb.toString();
		}
	}
}
