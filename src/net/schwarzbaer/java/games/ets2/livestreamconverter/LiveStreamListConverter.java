package net.schwarzbaer.java.games.ets2.livestreamconverter;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

import net.schwarzbaer.gui.GUI;
import net.schwarzbaer.gui.StandardMainWindow;

public final class LiveStreamListConverter implements ActionListener {
	
	// http://yp.shoutcast.com/sbin/tunein-station.pls?id=709938   [ DigitalGunfire.com ]
	// http://yp.shoutcast.com/sbin/tunein-station.pls?id=1591787  Sanctuary Radio
	// http://yp.shoutcast.com/sbin/tunein-station.pls?id=359487   -=- tormented radio -=-
	// http://yp.shoutcast.com/sbin/tunein-station.pls?id=180864   -=RantRadio Industrial=-
	// http://yp.shoutcast.com/sbin/tunein-station.pls?id=56499    sdx's synthetic experience!
	// http://yp.shoutcast.com/sbin/tunein-station.pls?id=560047   : ampedOut :
	
	private static final String FILENAME_BASECONFIG = "fileLocations.cfg";
	private static final String FILENAME_STATIONS_LIST = "known_stations.cfg";

	public static void main(String[] args) {
		
		LiveStreamListConverter converter = new LiveStreamListConverter();
		converter.createGUI();
		converter.readBaseConfig();
		converter.readStationList();
		for(Station station : converter.stationList) {
			System.out.println("station: "+station);
		}
	}

	private StandardMainWindow mainWindow;
	private JTextField ets2listFileNameTextField;
	private JTextField playlistFileNameTextField;
	private JTextArea ets2listContentTextArea;
	private JTextArea playlistContentTextArea;
	private JFileChooser ets2listFileChooser;
	private JFileChooser playlistFileChooser;
	private File ets2listFile;
	private File playlistFile;
	private Vector<Station> stationList;
	private Vector<StreamAdress> adressList;
	
	public LiveStreamListConverter() {
		ets2listFile = null;
		playlistFile = null;
		stationList = new Vector<Station>();
		adressList = new Vector<StreamAdress>();
	}
	
	private void createGUI() {
		GUI.setSystemLookAndFeel();
		
		ets2listFileChooser = new JFileChooser("./");
		playlistFileChooser = new JFileChooser("./");
		ets2listFileChooser.setFileFilter(new FileNameExtensionFilter("SII file(*.sii)","sii"));
		playlistFileChooser.setFileFilter(new FileNameExtensionFilter("SII file(*.sii)","sii"));
		
		JPanel fileLabelPanel = new JPanel(new GridLayout(2,1,3,3));
		JPanel fileNamePanel = new JPanel(new GridLayout(2,1,3,3));
		JPanel fileSelectButtonPanel = new JPanel(new GridLayout(2,1,3,3));
		
		ets2listFileNameTextField = GUI.createOutputTextField(20);
		playlistFileNameTextField = GUI.createOutputTextField(20);
		GUI.addLabelAndField(fileLabelPanel, fileNamePanel, "ETS2 file", ets2listFileNameTextField);
		GUI.addLabelAndField(fileLabelPanel, fileNamePanel, "playlist" , playlistFileNameTextField);
		fileSelectButtonPanel.add(GUI.createButton("select", "select ETS2 file", this));
		fileSelectButtonPanel.add(GUI.createButton("select", "select playlist file", this));
		
		JPanel fileSelectPanel = new JPanel(new BorderLayout(3,3));
		fileSelectPanel.add(fileLabelPanel,BorderLayout.WEST);
		fileSelectPanel.add(fileNamePanel,BorderLayout.CENTER);
		fileSelectPanel.add(fileSelectButtonPanel,BorderLayout.EAST);
		
		ets2listContentTextArea = createContentTextArea();
		playlistContentTextArea = createContentTextArea();
		
		JPanel fileContentPanel = new JPanel(new GridLayout(1,2,3,3));
		fileContentPanel.add(ets2listContentTextArea);
		fileContentPanel.add(playlistContentTextArea);
		
		JPanel buttonPanel = new JPanel(new GridLayout(1,3,3,3));
		buttonPanel.add(GUI.createButton("create ETS2 file", "create ETS2 file", this));
		buttonPanel.add(GUI.createButton("import station adresses", "import station adresses", this));
		buttonPanel.add(GUI.createButton("create playlist file", "create playlist file", this));
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		contentPane.add(fileSelectPanel,BorderLayout.NORTH);
		contentPane.add(fileContentPanel,BorderLayout.CENTER);
		contentPane.add(buttonPanel,BorderLayout.SOUTH);
		
		mainWindow = new StandardMainWindow("ETS2 - livestream list converter");
		mainWindow.startGUI(contentPane);
		mainWindow.setSizeAsMinSize();
	}

	private JTextArea createContentTextArea() {
		JTextArea textArea = new JTextArea(30,50);
		textArea.setBorder(BorderFactory.createEtchedBorder());
		textArea.setEditable(false);
		return textArea;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getActionCommand().equals("select ETS2 file")) {
			if (ets2listFileChooser.showSaveDialog(mainWindow)==JFileChooser.APPROVE_OPTION) {
				ets2listFile = ets2listFileChooser.getSelectedFile();
				ets2listFileNameTextField.setText(ets2listFile.getPath());
				writeBaseConfig();
			}
			return;
		}
		if (e.getActionCommand().equals("select playlist file")) {
			if (playlistFileChooser.showSaveDialog(mainWindow)==JFileChooser.APPROVE_OPTION) {
				playlistFile = playlistFileChooser.getSelectedFile();
				playlistFileNameTextField.setText(playlistFile.getPath());
				writeBaseConfig();
			}
			return;
		}
		if (e.getActionCommand().equals("create ETS2 file")) {
			String content = createETS2streamlist(adressList);
			writeContentTo(content,ets2listFile);
			ets2listContentTextArea.setText(content);
			return;
		}
		if (e.getActionCommand().equals("create playlist file")) {
			String content = createPlaylist(adressList);
			writeContentTo(content,playlistFile);
			playlistContentTextArea.setText(content);
			return;
		}
		if (e.getActionCommand().equals("import station adresses")) {
			adressList.clear();
			for (Station station: stationList) {
				Vector<StreamAdress> streamAdresses = station.getStreamAdresses();
				if (streamAdresses!=null) {
					adressList.addAll(streamAdresses);
					System.out.println("station: "+station);
					for (StreamAdress addr: streamAdresses)
						System.out.println("\t"+addr);
				}
			}
			return;
		}
		
	}

	private void writeContentTo(String content, File outputFile) {
		PrintWriter output;
		try { output = new PrintWriter(outputFile); }
		catch (FileNotFoundException e) { e.printStackTrace(); return; }
		output.println(content);
		output.close();
	}

	private String createPlaylist(Vector<StreamAdress> adressList) {
		StringBuilder sb = new StringBuilder();
		sb.append("[playlist]").append("\r\n");
		sb.append("numberofentries=").append(adressList.size()).append("\r\n");
		for (int i=0; i<adressList.size(); i++) {
			StreamAdress adress = adressList.get(i);
			sb.append(String.format("File%d=%s\r\n", i+1,adress.url));
			sb.append(String.format("Title%d=%s\r\n", i+1,adress.name));
			sb.append(String.format("Length%d=-1\r\n", i+1));
		}
		sb.append("Version=2").append("\r\n");
		return sb.toString();
	}

	private String createETS2streamlist(Vector<StreamAdress> adressList) {
		StringBuilder sb = new StringBuilder();
		sb.append("SiiNunit\r\n");
		sb.append("{\r\n");
		sb.append("live_stream_def : _nameless.35BF.92E8 {\r\n");
		for (int i=0; i<adressList.size(); i++) {
			StreamAdress adress = adressList.get(i);
			sb.append(String.format("stream_data[]: \"%s|%s\"\r\n", adress.url,adress.name));
		}
		sb.append("}\r\n");
		sb.append("}\r\n");
		return sb.toString();
	}

	private void readStationList() {
		BufferedReader input;
		try { input = new BufferedReader( new FileReader(FILENAME_STATIONS_LIST) ); } catch (FileNotFoundException e) { return; }
		String str;
		try {
			while( (str=input.readLine())!=null ) {
				if (str.startsWith("url" )) {
					int n = parseFieldNumber(str,"url".length());
					if (n<0) { System.out.println("Can't read field number in\""+str+"\""); continue; }
					String value = parseFieldValue(str);
					while (stationList.size()<=n) stationList.add(new Station());
					stationList.get(n).setURL(value);
				}
				if (str.startsWith("name")) {
					int n = parseFieldNumber(str,"name".length());
					if (n<0) { System.out.println("Can't read field number in\""+str+"\""); continue; }
					String value = parseFieldValue(str);
					while (stationList.size()<=n) stationList.add(new Station());
					stationList.get(n).setName(value);
				}
			}
		} catch (IOException e1) {}
		try { input.close(); } catch (IOException e) {}
	}

	private String parseFieldValue(String str) {
		int pos = str.indexOf('=');
		if (pos<0) return null;
		return str.substring(pos+1);
	}

	private int parseFieldNumber(String str, int fromIndex) {
		int end = str.indexOf('=',fromIndex);
		if (end<0) return -1;
		try { return Integer.parseInt(str.substring(fromIndex,end)); }
		catch (NumberFormatException e) { return -1; }
	}

	private void readBaseConfig() {
		BufferedReader input;
		try { input = new BufferedReader( new FileReader(FILENAME_BASECONFIG) ); } catch (FileNotFoundException e) { return; }
		String str;
		try {
			while( (str=input.readLine())!=null ) {
				if (str.startsWith("ets2listFile=")) { ets2listFile = new File(str.substring("ets2listFile=".length())); System.out.println("Found predefined ets2list file in config:"+ets2listFile.getPath()); ets2listFileNameTextField.setText(ets2listFile.getPath()); ets2listFileChooser.setSelectedFile(ets2listFile); }
				if (str.startsWith("playlistFile=")) { playlistFile = new File(str.substring("playlistFile=".length())); System.out.println("Found predefined playlist file in config:"+playlistFile.getPath()); playlistFileNameTextField.setText(playlistFile.getPath()); playlistFileChooser.setSelectedFile(playlistFile); }
			}
		} catch (IOException e1) {}
		try { input.close(); } catch (IOException e) {}
	}

	private void writeBaseConfig() {
		PrintWriter output;
		try { output = new PrintWriter(FILENAME_BASECONFIG); }
		catch (FileNotFoundException e) { e.printStackTrace(); return; }
		if (ets2listFile!=null) output.println("ets2listFile="+ets2listFile.getPath());
		if (playlistFile!=null) output.println("playlistFile="+playlistFile.getPath());
		output.close();
	}
	
	private static class StreamAdress {
		
		private String url;
		private String name;

		public StreamAdress(String name, String url) {
			this.name = name;
			this.url = url;
		}

		@Override
		public String toString() {
			return "StreamAdress [ name=\"" + name + "\", url=\"" + url + "\" ]";
		}
		
	}
	
	private static class Station {
		
		private String url;
		private String name;

		public Station() {
			url = null;
			name = null;
		}

		public void setURL (String url ) { this.url  = url;  }
		public void setName(String name) { this.name = name; }

		@Override
		public String toString() {
			return "Station [ name=\"" + name + "\", url=\"" + url + "\" ]";
		}
		
		public Vector<StreamAdress> getStreamAdresses() {
			if (url==null) return null;
			Object obj;
			try { obj = new URL(url).getContent(); }
			catch (MalformedURLException e) { e.printStackTrace(); return null; }
			catch (IOException e) { e.printStackTrace(); return null; }
			if (obj==null) return null;
			String content = getContent(obj);
			if (content==null) return null;
			
			BufferedReader input = new BufferedReader( new StringReader(content) );
			Vector<StreamAdress> adresses = new Vector<StreamAdress>();
			String str;
			try {
				while( (str=input.readLine())!=null ) {
					if (str.startsWith("File")) {
						int pos = str.indexOf('=');
						if (pos>=0)
							adresses.add(
								new StreamAdress(
									String.format("%s(%d)",name,adresses.size()+1),
									str.substring(pos+1)
								)
							);
					}
				}
			} catch (IOException e1) {}
			
			try { input.close(); } catch (IOException e) {}
			return adresses;
		}

		private String getContent(Object obj) {
			if (obj==null) return null;
			if (obj instanceof String) return (String)obj;
			if (obj instanceof InputStream) {
				StringBuilder sb = new StringBuilder();
				byte[] buffer = new byte[10000];
				InputStream input = (InputStream)obj;
				try {
					int len;
					while( (len=input.read(buffer))>=0 )
						sb.append(new String(buffer,0,len));
				} catch (IOException e) {}
				try { input.close(); } catch (IOException e) {}
				return sb.toString();
			}
			System.out.println("Unknown content type of station list: "+obj.getClass().getCanonicalName());
			return null;
		}
	}
}
