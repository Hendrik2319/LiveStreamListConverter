package net.schwarzbaer.java.tools.livestreamlistconverter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Vector;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import net.schwarzbaer.java.lib.gui.Disabler;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.ProgressDialog;
import net.schwarzbaer.java.lib.gui.StandardMainWindow;
import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.tools.livestreamlistconverter.OutputFormat.FormatEnum;

public final class LiveStreamListConverter implements ActionListener, BaseConfig.ExternalIF, Outputter.ExternalIF {
	
	static final String FILENAME_BASECONFIG    = "LiveStreamListConverter.FileLocations.cfg";
	static final String FILENAME_STATIONS_LIST = "LiveStreamListConverter.KnownStations.cfg";

	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		final LiveStreamListConverter converter = new LiveStreamListConverter();
		converter.baseConfig.readFromFile();
		converter.readStationListFromFile();
		converter.updateGUIAccess();
		
		boolean flag_automatic = false;
		boolean flag_keep_gui  = false;
		
		for (int i=0; i<args.length; i++) {
			if (args[i].equalsIgnoreCase("-automatic")) flag_automatic = true;
			if (args[i].equalsIgnoreCase("-keepgui")) flag_keep_gui = true;
		}
		
		if (flag_automatic) {
			ProgressDialog.runWithProgressDialog(converter.mainWindow, "Progress", 200, pd -> {
				converter.enableGUI(false);
				if (!pd.wasCanceled()) converter.importStationAdressesTask(pd);
				converter.forEachFormat((fe, outputter) -> {
					if (!pd.wasCanceled())
						outputter.generateAndWriteContentToFile(pd);
				});
				converter.enableGUI(true);
			});
			
			if (!flag_keep_gui) converter.mainWindow.dispose();
		}
	}
	
	enum ActionCommands
	{
		ImportStationAdresses, EditConfigFiles, GenerateAllFiles, Config,
	}

	private final StandardMainWindow mainWindow;
	private final JTabbedPane tabbedPane;
	private final JTextArea stationListTextArea;
	private final JScrollPane stationListTextAreaScrollPane;
	private final JList<Station> stationResponsesStationList;
	private final Vector<StreamAdress> adressList;
	private final Disabler<ActionCommands> disabler;
	private final Map<FormatEnum, Outputter> outputerMap;
	private final BaseConfig baseConfig;
	private final StationList stationList;
	private final int tabIndexStationAdresses;
	private final StationListTable stationListTable;
	
	public LiveStreamListConverter()
	{
		adressList = new Vector<>();
		stationList = new StationList();
		
		outputerMap = new EnumMap<>(FormatEnum.class);
		baseConfig = new BaseConfig(this);
		for (FormatEnum fe : FormatEnum.values())
			outputerMap.put(fe, new Outputter(baseConfig, fe.create.get(), this));
		
		mainWindow = new StandardMainWindow("Livestream List Converter");
		
		disabler = new Disabler<>();
		disabler.setCareFor(ActionCommands.values());
		
		JToolBar toolBar = new JToolBar();
		toolBar.setFloatable(false);
		
		toolBar.add(createButton("Import Station Adresses", ActionCommands.ImportStationAdresses));
		toolBar.add(createButton("Generate All Files", GrayCommandIcons.IconGroup.Save , ActionCommands.GenerateAllFiles));
		toolBar.addSeparator();
		toolBar.add(createButton("Edit Config Files", baseConfig.texteditorPath!=null, ActionCommands.EditConfigFiles));
		toolBar.add(createButton("Config", ActionCommands.Config));
		
		stationListTextArea = new JTextArea();
		stationListTextArea.setEditable(false);
		stationListTextAreaScrollPane = new JScrollPane(stationListTextArea);
		stationListTextAreaScrollPane.setBorder(
				BorderFactory.createCompoundBorder(
						BorderFactory.createEmptyBorder(2,2,2,2),
						stationListTextAreaScrollPane.getBorder()
				)
		);
		
		JTextArea stationResponsesOutput = new JTextArea();
		stationResponsesOutput.setEditable(false);
		stationResponsesOutput.setLineWrap(false);
		
		Color stationWithNoResponse = new Color(0xf0f0f0);
		Function<Object, String> strConverter = obj -> obj instanceof Station station ? station.name : obj.toString();
		Function<Object, Color> colorizer = obj -> obj instanceof Station station && station.stationResponse==null ? stationWithNoResponse : null;
		Tables.NonStringRenderer<Station> renderer = new Tables.NonStringRenderer<>(strConverter);
		renderer.setBackgroundColorizer(colorizer);
		
		stationResponsesStationList = new JList<>();
		stationResponsesStationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		stationResponsesStationList.setCellRenderer(renderer);
		stationResponsesStationList.addListSelectionListener(ev -> {
			int index = stationResponsesStationList.getSelectedIndex();
			Station station = stationList.getStation(index);
			if (station==null)
			{
				if (index<0)
					stationResponsesOutput.setText("<no station selected>");
				else
					stationResponsesOutput.setText("<unexpected NULL station>");
				return;
			}
			String stationResponse = station.stationResponse;
			if (stationResponse==null)
				stationResponsesOutput.setText("<received no response from station>");
			else
				stationResponsesOutput.setText(stationResponse);
		});
		
		JSplitPane stationResponsesPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
		stationResponsesPanel.setLeftComponent(new JScrollPane(stationResponsesStationList));
		stationResponsesPanel.setRightComponent(new JScrollPane(stationResponsesOutput));
		
		tabbedPane = new JTabbedPane();
		tabbedPane.addTab("Input: Stations", stationListTable = new StationListTable(mainWindow, stationList, this::readStationListFromFile));
		tabIndexStationAdresses = tabbedPane.getTabCount();
		tabbedPane.addTab("Input: Station Adresses", stationListTextAreaScrollPane);
		tabbedPane.addTab("Input: Station Responses", stationResponsesPanel);
		
		forEachFormat((fe, outputter) -> {
			String tabTitle = "Output: %s".formatted(outputter.outputFormat.fileLabel);
			int tabCount = tabbedPane.getTabCount();
			tabbedPane.addTab( tabTitle, outputter.createPanel(mainWindow, ()->tabbedPane.getModel().setSelectedIndex(tabCount)) );
		});
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		contentPane.add(toolBar,BorderLayout.PAGE_START);
		contentPane.add(tabbedPane,BorderLayout.CENTER);
		
		mainWindow.startGUI(contentPane, 1000,800);
		//mainWindow.setSizeAsMinSize();
	}

	private JButton createButton(String title, GrayCommandIcons.IconGroup iconGroup, ActionCommands ac)
	{
		JButton comp = createButton(title, true, ac);
		if (iconGroup!=null)
		{
			comp.setIcon        (iconGroup.getEnabledIcon ());
			comp.setDisabledIcon(iconGroup.getDisabledIcon());
		}
		return comp;
	}
	private JButton createButton(String title, ActionCommands ac)
	{
		return createButton( title, true, ac );
	}
	private JButton createButton(String title, boolean enabled, ActionCommands ac)
	{
		JButton comp = new JButton(title);
		comp.setEnabled(enabled);
		comp.addActionListener(this);
		comp.setActionCommand( ac.name() );
		disabler.add(ac, comp);
		return comp;
	}
	
	static JButton createButton(String title, GrayCommandIcons.IconGroup iconGroup, ActionListener al)
	{
		JButton comp = createButton(title, true, al);
		if (iconGroup!=null)
		{
			comp.setIcon        (iconGroup.getEnabledIcon ());
			comp.setDisabledIcon(iconGroup.getDisabledIcon());
		}
		return comp;
	}
	
	static JButton createButton(String title, ActionListener al)
	{
		return createButton(title, true, al);
	}
	
	static JButton createButton(String title, boolean enabled, ActionListener al)
	{
		JButton comp = new JButton(title);
		comp.setEnabled(enabled);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		ActionCommands actionCommand;
		try { actionCommand = ActionCommands.valueOf(e.getActionCommand()); }
		catch (Exception e1) { return; }
		
		switch (actionCommand)
		{
		case ImportStationAdresses:
			ProgressDialog.runWithProgressDialog(mainWindow, "Progress", 200, pd -> {
				enableGUI(false);
				importStationAdressesTask(pd);
				enableGUI(true);
			} );
			break;
			
		case EditConfigFiles:
			if (baseConfig.texteditorPath!=null)
				try {
					execute( baseConfig.texteditorPath, FILENAME_BASECONFIG, FILENAME_STATIONS_LIST );
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			break;
			
		case GenerateAllFiles:
			ProgressDialog.runWithProgressDialog(mainWindow, "Progress", 200, pd -> {
				enableGUI(false);
				forEachFormat((fe, outputter) -> {
					if (!pd.wasCanceled())
						outputter.generateAndWriteContentToFile(pd);
				});
				enableGUI(true);
			} );
			break;
			
		case Config:
			BaseConfigDialog.showDialog(mainWindow, baseConfig);
			updateGUIAccess();
			break;
		}
	}

	@Override
	public void enableGUI(boolean enable)
	{
		stationListTable.setEnabled(enable);
		forEachFormat((fe, outputter) -> {
			outputter.setPanelEnabled(enable);
		});
		disabler.setEnable(ac ->  switch (ac) {
			case ImportStationAdresses, GenerateAllFiles
				-> enable && stationList.hasStations();
			
			case Config
				-> enable;
				
			case EditConfigFiles
				-> enable && baseConfig.texteditorPath!=null;
		});
	}

	private void updateGUIAccess() {
		enableGUI(true);
	}

	static void execute(String... cmdarray) throws IOException {
		System.out.printf("Call command: %s%n", Arrays.toString(cmdarray));
		Runtime.getRuntime().exec(cmdarray);
	}

	@Override
	public Vector<StreamAdress> getAdressList()
	{
		return adressList;
	}

	@Override
	public void forEachFormat(BiConsumer<FormatEnum, Outputter> action)
	{
		for (FormatEnum fe : FormatEnum.values())
		{
			Outputter outputter = outputerMap.get(fe);
			if (outputter!=null)
				action.accept(fe, outputter);
		}
	}

	private void readStationListFromFile()
	{
		stationList.readFromFile();
		stationList.setStationsInList(stationResponsesStationList);
		stationListTable.updateTable();
	}

	private void importStationAdressesTask(ProgressDialog pd) {
		tabbedPane.getModel().setSelectedIndex(tabIndexStationAdresses);
		
		System.out.println();
		stationList.forEachStation((index,station) -> {
			System.out.printf("station: %s%n", station);
			return true;
		});
		
		System.out.println();
		System.out.println("Import station adresses ...");
		adressList.clear();
		SwingUtilities.invokeLater(()->{
			pd.setTaskTitle("Import station adresses:");
			pd.setValue(0, stationList.getStationCount());
			stationListTextArea.setText("");;
		});
		stationList.forEachStation((index,station) -> {
			if (pd.wasCanceled()) return false;
			Vector<StreamAdress> streamAdresses = station.readStreamAdressesFromWeb();
			if (streamAdresses!=null) {
				System.out.printf("station: %s%n", station);
				SwingUtilities.invokeLater(()->{
					stationListTextArea.append(String.format("station: %s\r\n", station.name));
					stationListTextArea.append(String.format("  list: %s\r\n", station.url));
				});
				for (StreamAdress addr: streamAdresses) {
					boolean ignored = stationList.isIgnoredStreamURL(addr.url);
					if (!ignored) adressList.add(addr);
					String ignoredStr = ignored ? "[IGNORED] " : "";
					System.out.printf("\t%s%s%n", ignoredStr, addr);
					SwingUtilities.invokeLater(()->{
						stationListTextArea.append(String.format("    %s%s%n", ignoredStr, addr.url));
					});
				}
				SwingUtilities.invokeLater(()->{
					scrolltoEnd(stationListTextAreaScrollPane);
				});
			}
			int progress = index+1;
			SwingUtilities.invokeLater(()->{
				pd.setValue(progress);
			});
			return !pd.wasCanceled();
		});
		if (pd.wasCanceled()) {
			adressList.clear();
			SwingUtilities.invokeLater(()->{
				stationListTextArea.setText("");
			});
		}
	}

	static void scrolltoEnd(JScrollPane scrollPane)
	{
		JScrollBar vertScrollBar = scrollPane.getVerticalScrollBar();
		int pos = vertScrollBar.getMaximum()-vertScrollBar.getVisibleAmount();
		if (pos > vertScrollBar.getMinimum())
			vertScrollBar.setValue(pos);
	}

	static String parseValue(String line, String prefix)
	{
		if (line.startsWith(prefix))
			return line.substring(prefix.length());
		return null;
	}
}
