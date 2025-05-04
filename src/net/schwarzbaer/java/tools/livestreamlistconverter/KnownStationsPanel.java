package net.schwarzbaer.java.tools.livestreamlistconverter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellRenderer;

import net.schwarzbaer.java.lib.gui.Disabler;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.gui.Tables.LabelRendererComponent;
import net.schwarzbaer.java.lib.gui.Tables.SimplifiedColumnConfig;
import net.schwarzbaer.java.lib.system.Settings.DefaultAppSettings.SplitPaneDividersDefinition;
import net.schwarzbaer.java.tools.livestreamlistconverter.LiveStreamListConverter.AppSettings;

class KnownStationsPanel extends JPanel
{
	private static final long serialVersionUID = -6522689930616803901L;
	
	private final KnownStations knownStations;
	private final Disabler<Commands> disabler;
	private final StationListPanel stationListPanel;
	private final IgnoredStreamURLsPanel ignoredStreamURLsPanel;
	private final JSplitPane splitPane;

	KnownStationsPanel(Window parent, KnownStations knownStations, Runnable readKnownStations)
	{
		super(new BorderLayout());
		this.knownStations = knownStations;
		
		disabler = new Disabler<>();
		disabler.setCareFor(Commands.values());
		
		JToolBar toolBar = new JToolBar();
		toolBar.setFloatable(false);
		toolBar.add(addCommand(Commands.ReadList , LiveStreamListConverter.createButton("Read List from File", GrayCommandIcons.IconGroup.Folder, e -> {
			readKnownStations.run();
		})));
		toolBar.add(addCommand(Commands.WriteList, LiveStreamListConverter.createButton("Write List to File", GrayCommandIcons.IconGroup.Save, e -> {
			this.knownStations.writeToFile();
			resetChangesFlag();
		})));
		
		splitPane = new JSplitPane(
				JSplitPane.VERTICAL_SPLIT, true,
				stationListPanel = new StationListPanel(parent),
				ignoredStreamURLsPanel = new IgnoredStreamURLsPanel(parent)
		);
		
		add(toolBar, BorderLayout.PAGE_START);
		add(splitPane, BorderLayout.CENTER);
	}

	void registerAt(SplitPaneDividersDefinition<AppSettings.ValueKey> splitPaneDividersDefinition)
	{
		splitPaneDividersDefinition.add(splitPane, AppSettings.ValueKey.SplitPaneDivider_KnownStationsPanelSplitPane);
	}

	enum Commands { ReadList, WriteList, StationDelete, StationMoveUp, StationMoveDown, IgnoredURLDelete }
	
	private <C extends JComponent> C addCommand(Commands command, C comp)
	{
		disabler.add(command, comp);
		return comp;
	}
	
	@Override
	public void setEnabled(boolean enabled)
	{
		super.setEnabled(enabled);
		stationListPanel.setEnabled(enabled);
		ignoredStreamURLsPanel.setEnabled(enabled);
		disabler.setEnable(command ->
			switch (command)
			{
			case ReadList  -> enabled;
			case WriteList -> enabled && (stationListPanel.hasChanges() || ignoredStreamURLsPanel.hasChanges());
			
			case StationDelete, StationMoveDown, StationMoveUp
				-> enabled && stationListPanel.canBeEnabled(command);
			case IgnoredURLDelete
				-> enabled && ignoredStreamURLsPanel.canBeEnabled(command);
			}
		);
	}
	
	private void updateGuiAccess()
	{
		setEnabled(isEnabled());
	}

	void updateTable()
	{
		stationListPanel.updateTable();
		ignoredStreamURLsPanel.updateTable();
	}
	
	void resetChangesFlag()
	{
		stationListPanel.resetChangesFlag();
		ignoredStreamURLsPanel.resetChangesFlag();
		updateGuiAccess();
	}

	private class IgnoredStreamURLsPanel extends JPanel
	{
		private static final long serialVersionUID = -6629733887524199128L;
		
		private final JTable table;
		private final IgnoredStreamURLsTableModel tableModel;

		IgnoredStreamURLsPanel(Window parent)
		{
			super(new BorderLayout());
			setBorder(BorderFactory.createTitledBorder("Ignored Stream URLs"));
			
			tableModel = new IgnoredStreamURLsTableModel();
			table = new JTable(tableModel);
			tableModel.setTable(table);
			table.setRowSorter(new Tables.SimplifiedRowSorter(tableModel));
			table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
			table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
			tableModel.setColumnWidths(table);
			tableModel.setDefaultCellEditorsAndRenderers();
			table.getSelectionModel().addListSelectionListener(ev -> {
				updateGuiAccess();
			});
			
			JScrollPane tableScrollPane = new JScrollPane(table);
			
			JToolBar toolBar = new JToolBar();
			toolBar.setFloatable(false);
			toolBar.add(addCommand(Commands.IgnoredURLDelete, LiveStreamListConverter.createButton("Delete Selected", GrayCommandIcons.IconGroup.Delete, e -> {
				List<String> urls = Arrays
					.stream(table.getSelectedRows())
					.filter(i -> i>=0)
					.map(table::convertRowIndexToModel)
					.filter(i -> i>=0)
					.mapToObj(index -> tableModel.getRow(index))
					.toList();
				
				Vector<String> msg = new Vector<>();
				msg.add("Are you sure?");
				msg.add("Do you really want to delete %d urls:".formatted(urls.size()));
				for (int i=0; i<urls.size(); i++)
					msg.add("    [%d] %s".formatted(i+1, urls.get(i)));
				
				int result = JOptionPane.showConfirmDialog(parent, msg.toArray(String[]::new), "Delete Ignored Stream URLs", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
				if (result == JOptionPane.YES_OPTION)
				{
					knownStations.deleteIgnoredStreamURLs(urls);
					tableModel.updateTableData();
					tableModel.setHasChanges(true);
					updateGuiAccess();
				}
			})));
			
			add(toolBar, BorderLayout.PAGE_START);
			add(tableScrollPane, BorderLayout.CENTER);
		}
		
		void resetChangesFlag()
		{
			tableModel.setHasChanges(false);
		}

		boolean hasChanges()
		{
			return tableModel.hasChanges();
		}

		void updateTable()
		{
			tableModel.updateTableData();
		}

		@Override
		public void setEnabled(boolean enabled)
		{
			super.setEnabled(enabled);
			tableModel.setEnabled(enabled);
		}

		boolean canBeEnabled(Commands command)
		{
			return switch (command)
					{
					case IgnoredURLDelete -> true;
					default -> false;
					};
		}
	}
	
	private class IgnoredStreamURLsTableModel extends Tables.SimpleGetValueTableModel<String, IgnoredStreamURLsTableModel.ColumnID>
	{
		enum ColumnID implements Tables.AbstractGetValueTableModel.ColumnIDTypeInt<String>
		{
			Index ("#"   , Integer.class,  30, null),
			URL   ("URL" ,  String.class, 450, url -> url),
			;
			private final SimplifiedColumnConfig cfg;
			private final Function<String, ?> getValue;
			
			<V> ColumnID(String name, Class<V> columnClass, int width, Function<String,V> getValue)
			{
				this.cfg = new SimplifiedColumnConfig(name, columnClass, 20, -1, width, width);
				this.getValue = getValue;
			}
		
			@Override public SimplifiedColumnConfig getColumnConfig() { return cfg; }
			@Override public Function<String, ?> getGetValue() { return getValue; }
		}
	
		private boolean hasChanges;
		private boolean enabled;
	
		IgnoredStreamURLsTableModel()
		{
			super(ColumnID.values(), getSortedList(knownStations.ignoredStreamURLs));
			hasChanges = false;
			enabled = true;
		}
	

		private static String[] getSortedList(Set<String> data)
		{
			return data.stream().sorted().toArray(String[]::new);
		}
	
		void setEnabled(boolean enabled) { this.enabled = enabled; }
		void setHasChanges(boolean hasChanges) { this.hasChanges = hasChanges; }
		boolean hasChanges() { return hasChanges; }

		@Override
		public void setDefaultCellEditorsAndRenderers()
		{
			IgnoredStreamURLsTableCellRenderer tcr = new IgnoredStreamURLsTableCellRenderer();
			for (ColumnID columnID : ColumnID.values())
				switch (columnID)
				{
				case Index: case URL:
					setCellRenderer(columnID, tcr);
					break;
				}
		}
		
		private class IgnoredStreamURLsTableCellRenderer implements TableCellRenderer
		{
			private final LabelRendererComponent comp;
			
			IgnoredStreamURLsTableCellRenderer()
			{
				this.comp = new LabelRendererComponent();
			}

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowV, int columnV)
			{
				int rowM    = rowV   <0 ? -1 : table.convertRowIndexToModel(rowV);
				int columnM = columnV<0 ? -1 : table.convertColumnIndexToModel(columnV);
				ColumnID columnID = getColumnID(columnM);
				boolean isLastRow = rowM == IgnoredStreamURLsTableModel.super.getRowCount();
				
				String valueStr = value==null ? "" : value.toString();
				if (isLastRow && columnID==ColumnID.Index)
					valueStr = "+";
				
				comp.configureAsTableCellRendererComponent(table, null, valueStr, isSelected, hasFocus);
				
				int alignment = SwingConstants.LEFT;
				if (columnID == ColumnID.Index)
					alignment = SwingConstants.CENTER;
				else if (columnID!=null && Number.class.isAssignableFrom(columnID.cfg.columnClass))
					alignment = SwingConstants.RIGHT;
				comp.setHorizontalAlignment(alignment);
				
				return comp;
			}
		}

		@Override
		public int getRowCount()
		{
			return super.getRowCount() + 1;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex, ColumnID columnID)
		{
			if (columnID == ColumnID.Index) return rowIndex+1;
			return super.getValueAt(rowIndex, columnIndex, columnID);
		}

		@Override
		protected boolean isCellEditable(int rowIndex, int columnIndex, ColumnID columnID)
		{
			if (!enabled)
				return false;
			
			if (columnID == null)
				return false;
			
			return switch (columnID)
					{
					case Index -> false;
					case URL -> true;
					};
		}

		@Override
		protected void setValueAt(Object aValue, int rowIndex, int columnIndex, ColumnID columnID)
		{
			if (columnID==null || aValue==null)
				return;
			
			String oldUrl = rowIndex == super.getRowCount() ? null : getRow(rowIndex);
			
			switch (columnID)
			{
			case Index: break;
			case URL  :
				String newUrl = (String)aValue;
				if (oldUrl==null)
					knownStations.addIgnoredStreamURL( newUrl );
				else
					knownStations.replaceIgnoredStreamURL( oldUrl, newUrl );
				break;
			}
			
			SwingUtilities.invokeLater(() -> {
				hasChanges = true;
				updateTableData();
			});
		}

		void updateTableData()
		{
			setData( getSortedList( knownStations.ignoredStreamURLs ) );
			repaint();
			updateGuiAccess();
		}
	}

	private class StationListPanel extends JPanel
	{
		private static final long serialVersionUID = 887703065776534388L;
		
		private final JTable table;
		private final StationListTableModel tableModel;
		
		StationListPanel(Window parent)
		{
			super(new BorderLayout());
			setBorder(BorderFactory.createTitledBorder("Stations"));
			
			tableModel = new StationListTableModel();
			table = new JTable(tableModel);
			tableModel.setTable(table);
			table.setRowSorter(new Tables.SimplifiedRowSorter(tableModel));
			table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
			table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
			tableModel.setColumnWidths(table);
			tableModel.setDefaultCellEditorsAndRenderers();
			table.getSelectionModel().addListSelectionListener(ev -> {
				updateGuiAccess();
			});
			
			JScrollPane tableScrollPane = new JScrollPane(table);
			
			JToolBar toolBar = new JToolBar();
			toolBar.setFloatable(false);
			//toolBar.add(LiveStreamListConverter.createButton("Show Column Widths", e -> System.out.printf("ColumnWidths: %sy%n", StationListTableModel.getColumnWidthsAsString(table))));
			//toolBar.addSeparator();
			toolBar.add(addCommand(Commands.StationDelete, LiveStreamListConverter.createButton("Delete Selected", GrayCommandIcons.IconGroup.Delete, e -> {
				int[] selectedRowsM = Arrays
					.stream(table.getSelectedRows())
					.filter(i -> i>=0)
					.map(table::convertRowIndexToModel)
					.filter(i -> i>=0)
					.sorted()
					.toArray();
				
				Vector<String> msg = new Vector<>();
				msg.add("Are you sure?");
				msg.add("Do you really want to delete %d stations:".formatted(selectedRowsM.length));
				for (int rowM : selectedRowsM)
				{
					Station station = knownStations.getStation(rowM);
					String name = station==null ? null : station.name;
					if (name==null) name = "<unnamed station>";
					msg.add("    [%d] %s".formatted(rowM+1, name));
				}
				int result = JOptionPane.showConfirmDialog(parent, msg.toArray(String[]::new), "Delete Stations", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
				if (result == JOptionPane.YES_OPTION)
				{
					knownStations.deleteStations(selectedRowsM);
					tableModel.fireTableUpdate();
					tableModel.setHasChanges(true);
					updateGuiAccess();
				}
			})));
			toolBar.add(addCommand(Commands.StationMoveUp  , LiveStreamListConverter.createButton("Move Up"  , GrayCommandIcons.IconGroup.Up  , e -> moveStation(-1))));
			toolBar.add(addCommand(Commands.StationMoveDown, LiveStreamListConverter.createButton("Move Down", GrayCommandIcons.IconGroup.Down, e -> moveStation(+1))));
			
			add(toolBar, BorderLayout.PAGE_START);
			add(tableScrollPane, BorderLayout.CENTER);
		}
		
		void resetChangesFlag()
		{
			tableModel.setHasChanges(false);
		}

		boolean hasChanges()
		{
			return tableModel.hasChanges();
		}

		void updateTable()
		{
			tableModel.fireTableUpdate();
		}

		@Override
		public void setEnabled(boolean enabled)
		{
			super.setEnabled(enabled);
			tableModel.setEnabled(enabled);
		}

		boolean canBeEnabled(Commands command)
		{
			return switch(command)
					{
					case StationDelete
						-> table.getSelectedRowCount() > 0;
					case StationMoveDown, StationMoveUp
						-> table.getSelectedRowCount() == 1;
					default
						-> false;
					};
		}

		private void moveStation(int inc)
		{
			if (table.getSelectedRowCount() != 1) return;
			int rowV = table.getSelectedRow();
			int rowM = rowV<0 ? -1 : table.convertRowIndexToModel(rowV);
			if (rowM<0) return;
			Integer newIndex = knownStations.moveStation(rowM, inc);
			if (newIndex!=null)
			{
				tableModel.fireTableUpdate();
				tableModel.setHasChanges(true);
				updateGuiAccess();
				table.setRowSelectionInterval(newIndex.intValue(), newIndex.intValue());
			}
		}
	}

	private class StationListTableModel extends Tables.SimpleGetValueTableModel<Station, StationListTableModel.ColumnID>
	{
		// ColumnWidths: [25, 305, 450, 46] in ModelOrdery
		enum ColumnID implements Tables.AbstractGetValueTableModel.ColumnIDTypeInt<Station>
		{
			Index ("#"   ,    Integer.class,  30, null),
			Name  ("Name",     String.class, 300, station -> station.name),
			URL   ("URL" ,     String.class, 450, station -> station.url ),
			Type  ("Type", SourceType.class,  50, station -> station.type),
			;
			private final SimplifiedColumnConfig cfg;
			private final Function<Station, ?> getValue;
			
			<V> ColumnID(String name, Class<V> columnClass, int width, Function<Station,V> getValue)
			{
				this.cfg = new SimplifiedColumnConfig(name, columnClass, 20, -1, width, width);
				this.getValue = getValue;
			}
		
			@Override public SimplifiedColumnConfig getColumnConfig() { return cfg; }
			@Override public Function<Station, ?> getGetValue() { return getValue; }
			
		}

		private boolean hasChanges;
		private boolean enabled;

		StationListTableModel()
		{
			super(ColumnID.values(), knownStations.stationList);
			hasChanges = false;
			enabled = true;
		}

		void setEnabled(boolean enabled) { this.enabled = enabled; }
		void setHasChanges(boolean hasChanges) { this.hasChanges = hasChanges; }
		boolean hasChanges() { return hasChanges; }

		@Override
		public void setDefaultCellEditorsAndRenderers()
		{
			setCellEditor(ColumnID.Type, new Tables.ComboboxCellEditor<>(SourceType.values()));
			
			StationListTableCellRenderer tcr = new StationListTableCellRenderer();
			for (ColumnID columnID : ColumnID.values())
				switch (columnID)
				{
				case Index: case Name: case URL: case Type:
					setCellRenderer(columnID, tcr);
					break;
				}
		}
		
		private class StationListTableCellRenderer implements TableCellRenderer
		{
			private final LabelRendererComponent comp;
			
			StationListTableCellRenderer()
			{
				this.comp = new LabelRendererComponent();
			}

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowV, int columnV)
			{
				int rowM    = rowV   <0 ? -1 : table.convertRowIndexToModel(rowV);
				int columnM = columnV<0 ? -1 : table.convertColumnIndexToModel(columnV);
				ColumnID columnID = getColumnID(columnM);
				boolean isLastRow = rowM == StationListTableModel.super.getRowCount();
				
				String valueStr = value==null ? "" : value.toString();
				if (isLastRow && columnID==ColumnID.Index)
					valueStr = "+";
				
				boolean isCellEditable = isCellEditable(rowM, columnM, columnID);
				Supplier<Color> getCustomBackground = isCellEditable && value==null && !isLastRow ? ()->Color.RED : null;
				comp.configureAsTableCellRendererComponent(table, null, valueStr, isSelected, hasFocus, getCustomBackground, null);
				
				int alignment = SwingConstants.LEFT;
				if (columnID == ColumnID.Index || columnID == ColumnID.Type)
					alignment = SwingConstants.CENTER;
				else if (columnID!=null && Number.class.isAssignableFrom(columnID.cfg.columnClass))
					alignment = SwingConstants.RIGHT;
				comp.setHorizontalAlignment(alignment);
				
				return comp;
			}
		}

		@Override
		public int getRowCount()
		{
			return super.getRowCount() + 1;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex, ColumnID columnID)
		{
			if (columnID == ColumnID.Index) return rowIndex+1;
			return super.getValueAt(rowIndex, columnIndex, columnID);
		}

		@Override
		protected boolean isCellEditable(int rowIndex, int columnIndex, ColumnID columnID)
		{
			if (!enabled)
				return false;
			
			if (columnID == null)
				return false;
			
			return switch (columnID)
					{
					case Index -> false;
					case Name, URL, Type -> true;
					};
		}

		@Override
		protected void setValueAt(Object aValue, int rowIndex, int columnIndex, ColumnID columnID)
		{
			if (columnID==null)
				return;
			
			Station row;
			boolean newRow = false;
			if (rowIndex == super.getRowCount())
			{
				row = knownStations.addNewStation();
				newRow = true;
			}
			else
				row = getRow(rowIndex);
			
			if (row==null)
				return;
			
			switch (columnID)
			{
			case Index: break;
			case Name: row.name = (String)aValue; break;
			case URL : row.url  = (String)aValue; break;
			case Type: row.type = (SourceType)aValue; break;
			}
			
			if (newRow)
				fireTableUpdate();
			
			hasChanges = true;
			updateGuiAccess();
		}
	}
}
