package net.schwarzbaer.java.tools.livestreamlistconverter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

import net.schwarzbaer.java.tools.livestreamlistconverter.OutputFormat.FormatEnum;

class BaseConfig
{
	interface ExternalIF
	{
		void forEachFormat(BiConsumer<FormatEnum, Outputter> action);
	}
	
	String texteditorPath = null;
	String filemanagerPath = null;
	private final ExternalIF externalIF;
	
	BaseConfig(ExternalIF externalIF)
	{
		this.externalIF = externalIF;
	}

	void readFromFile() {
		try (BufferedReader input = new BufferedReader( new InputStreamReader(new FileInputStream(LiveStreamListConverter.FILENAME_BASECONFIG), StandardCharsets.UTF_8)))
		{
			String line, valueStr;
			while( (line=input.readLine())!=null ) {
				String line_ = line;
				externalIF.forEachFormat((fe, outputter) -> {
					String pathStr, prefix = "outputFile.%s=".formatted(fe);
					if ( (pathStr = LiveStreamListConverter.parseValue( line_, prefix ))!=null )
					{
						File outputFile = new File(pathStr);
						System.out.printf("Found path to %s file in config: \"%s\"%n", outputter.outputFormat.fileLabel, outputFile.getAbsolutePath());
						outputter.setOutputFile( outputFile );
					}
				});
				//if ( (valueStr = LiveStreamListConverter.parseValue(line,"ets2listFile="))!=null ) { ets2listFile    = new File(valueStr); System.out.println("Found predefined ets2list file in config: \""+ets2listFile.getPath()+"\""); ets2listFileNameTextField.setText(ets2listFile.getPath()); ets2listFileChooser.setSelectedFile(ets2listFile); }
				//if ( (valueStr = LiveStreamListConverter.parseValue(line,"playlistFile="))!=null ) { playlistFile    = new File(valueStr); System.out.println("Found predefined playlist file in config: \""+playlistFile.getPath()+"\""); playlistFileNameTextField.setText(playlistFile.getPath()); playlistFileChooser.setSelectedFile(playlistFile); }
				if ( (valueStr = LiveStreamListConverter.parseValue(line,"texteditor="  ))!=null ) { texteditorPath  = valueStr; System.out.println("Found path to text editor in config: \""+texteditorPath +"\""); }
				if ( (valueStr = LiveStreamListConverter.parseValue(line,"filemanager=" ))!=null ) { filemanagerPath = valueStr; System.out.println("Found path to filemanager in config: \""+filemanagerPath+"\""); }
			}
		}
		catch (FileNotFoundException ex) {}
		catch (IOException ex) {
			System.err.printf("IOException while reading BaseConfig: %s%n", ex.getMessage());
			//ex.printStackTrace();
		}
	}

	void writeToFile()
	{
		try (PrintWriter output = new PrintWriter(LiveStreamListConverter.FILENAME_BASECONFIG, StandardCharsets.UTF_8))
		{
			externalIF.forEachFormat((fe, outputter) -> {
				File outputFile = outputter.getOutputFile();
				if (outputFile!=null)
					output.printf("outputFile.%s=%s%n", fe, outputFile.getAbsolutePath());
			});
			//if (ets2listFile   !=null) output.println("ets2listFile="+ets2listFile.getPath());
			//if (playlistFile   !=null) output.println("playlistFile="+playlistFile.getPath());
			if (texteditorPath !=null) output.println("texteditor="  +texteditorPath );
			if (filemanagerPath!=null) output.println("filemanager=" +filemanagerPath);
		}
		catch (FileNotFoundException ex) {
			System.err.printf("FileNotFoundException while writing BaseConfig: %s%n", ex.getMessage());
			//ex.printStackTrace();
		}
		catch (IOException ex) {
			System.err.printf("IOException while writing BaseConfig: %s%n", ex.getMessage());
			//ex.printStackTrace();
		}
	}
}
