package net.schwarzbaer.java.tools.livestreamlistconverter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import javax.swing.DefaultListModel;

class KnownStations
{
	final Vector<Station> stationList = new Vector<>();
	final Set<String> ignoredStreamURLs = new HashSet<>();

	Integer moveStation(int index, int inc)
	{
		int from = index;
		int to = from+inc;
		if (0<=Math.min(from, to) && Math.max(from, to)<stationList.size())
		{
			Station station = stationList.get(from);
			stationList.removeElementAt(from);
			stationList.insertElementAt(station, to);
			return to;
		}
		return null;
	}

	void deleteStations(int[] indexes)
	{
		Arrays.sort(indexes);
		for (int i=indexes.length-1; i>=0; i--)
		{
			int index = indexes[i];
			if (0<=index && index<stationList.size())
				stationList.removeElementAt(index);
		}
	}

	void replaceStationsInList(DefaultListModel<Station> listModel)
	{
		listModel.removeAllElements();
		listModel.addAll(stationList);
	}

	boolean hasStations() { return !stationList.isEmpty(); }
	int getStationCount() { return stationList.size(); }
	Station getStation(int index) { return index<0 || index>=stationList.size() ? null : stationList.get(index); }

	Station addNewStation()
	{
		Station station = new Station();
		stationList.add(station);
		return station;
	}

	interface ForEachStationAction
	{
		boolean doWith(int index, Station station);
	}
	
	void forEachStation(KnownStations.ForEachStationAction action)
	{
		boolean dontStop = true;
		for (int i=0; i<stationList.size() && dontStop; i++)
			dontStop = action.doWith(i, stationList.get(i));
	}

	boolean isIgnoredStreamURL(String url) { return ignoredStreamURLs.contains(url); }
	void addIgnoredStreamURL(String url) { ignoredStreamURLs.add(url); }
	void deleteIgnoredStreamURLs(List<String> urls) { urls.forEach(ignoredStreamURLs::remove); }

	void replaceIgnoredStreamURL(String oldUrl, String newUrl)
	{
		ignoredStreamURLs.remove(oldUrl);
		ignoredStreamURLs.add(newUrl);
	}

	void writeToFile()
	{
		File file = new File(LiveStreamListConverter.FILENAME_STATIONS_LIST);
		System.out.printf("Write StationList to file \"%s\" ...%n", file.getAbsolutePath());
		
		try (PrintWriter output = new PrintWriter(file, StandardCharsets.UTF_8))
		{
			if (!ignoredStreamURLs.isEmpty())
			{
				output.println("[IgnoredStreamURLs]");
				List<String> urls = ignoredStreamURLs.stream().sorted().toList();
				for (String url : urls)
					output.printf("url=%s%n", url);
				output.println();
			}
			
			if (!stationList.isEmpty())
			{
				for (Station station : stationList)
				{
					output.println("[Station]");
					if (station.url  != null) output.printf("url=%s%n" , station.url );
					if (station.name != null) output.printf("name=%s%n", station.name);
					if (station.type != null) output.printf("type=%s%n", station.type.name());
					output.println();
				}
			}
		}
		catch (IOException ex)
		{
			System.err.printf("IOException while writing StationList: %s%n", ex.getMessage());
			//ex.printStackTrace();
		}
		
		System.out.println("... done");
	}

	void readFromFile()
	{
		File file = new File(LiveStreamListConverter.FILENAME_STATIONS_LIST);
		System.out.printf("Read StationList from file \"%s\" ...%n", file.getAbsolutePath());
		
		stationList.clear();
		ignoredStreamURLs.clear();
		
		try (BufferedReader input = new BufferedReader( new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)))
		{
			String line, valueStr;
			Station station = null;
			boolean inIgnoredStreamURLsSection = false;
			while( (line=input.readLine())!=null )
			{
				if (line.toLowerCase().equals("[station]"))
				{
					stationList.add(station = new Station());
					inIgnoredStreamURLsSection = false;
				}
				if (line.toLowerCase().equals("[ignoredstreamurls]"))
				{
					station = null;
					inIgnoredStreamURLsSection = true;
				}
				
				if (station!=null)
				{
					if ( (valueStr = LiveStreamListConverter.parseValue(line,"url=" ))!=null ) station.url  = valueStr;
					if ( (valueStr = LiveStreamListConverter.parseValue(line,"name="))!=null ) station.name = valueStr;
					if ( (valueStr = LiveStreamListConverter.parseValue(line,"type="))!=null ) station.type = SourceType.parseSourceType(valueStr);
				}
				
				if (inIgnoredStreamURLsSection)
				{
					if ( (valueStr = LiveStreamListConverter.parseValue(line,"url=" ))!=null ) ignoredStreamURLs.add(valueStr);
				}
			}
		}
		catch (FileNotFoundException ex) {}
		catch (IOException ex) {
			System.err.printf("IOException while reading StationList: %s%n", ex.getMessage());
			//ex.printStackTrace();
		}
		
		System.out.println("... done");
	}
	
}