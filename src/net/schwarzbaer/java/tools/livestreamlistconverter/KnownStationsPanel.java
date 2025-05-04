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

	private class IgnoredStreamURLsPanel extends AbstractTablePanel<IgnoredStreamURLsTableModel>
	{
		private static final long serialVersionUID = -6629733887524199128L;

		IgnoredStreamURLsPanel(Window parent)
		{
			super("Ignored Stream URLs", IgnoredStreamURLsTableModel::new);
			
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
		}

		@Override
		void updateTable()
		{
			tableModel.updateTableData();
		}

		@Override
		boolean canBeEnabled(Commands command)
		{
			return switch (command)
					{
					case IgnoredURLDelete -> true;
					default -> false;
					};
		}
	}
	
	private class IgnoredStreamURLsTableModel extends AbstractTableModel<String, IgnoredStreamURLsTableModel.ColumnID>
	{
		enum ColumnID implements Tables.AbstractGetValueTableModel.ColumnIDTypeInt<String>
		{
			Index ("#"   , Integer.class,  30, null),
			URL   ("URL" ,  String.class, 450, url -> url),
			;
			private final Tables.SimplifiedColumnConfig cfg;
			private final Function<String, ?> getValue;
			
			<V> ColumnID(String name, Class<V> columnClass, int width, Function<String,V> getValue)
			{
				this.cfg = new Tables.SimplifiedColumnConfig(name, columnClass, 20, -1, width, width);
				this.getValue = getValue;
			}
		
			@Override public Tables.SimplifiedColumnConfig getColumnConfig() { return cfg; }
			@Override public Function<String, ?> getGetValue() { return getValue; }
		}
	
		IgnoredStreamURLsTableModel()
		{
			super(ColumnID.values(), getSortedList(knownStations.ignoredStreamURLs), ColumnID.Index);
		}
		
		private static Vector<String> getSortedList(Set<String> data)
		{
			List<String> list = data.stream().sorted().toList();
			return new Vector<>(list);
		}
		
		@Override
		protected boolean isColumnEditable(ColumnID columnID)
		{
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
			
			String oldUrl = rowIndex+1 == getRowCount() ? null : getRow(rowIndex);
			
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

	private class StationListPanel extends AbstractTablePanel<StationListTableModel>
	{
		private static final long serialVersionUID = 887703065776534388L;
		
		StationListPanel(Window parent)
		{
			super("Stations", StationListTableModel::new);
			
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

		@Override
		void updateTable()
		{
			tableModel.fireTableUpdate();
		}

		@Override
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
	}

	private class StationListTableModel extends AbstractTableModel<Station, StationListTableModel.ColumnID>
	{
		// ColumnWidths: [25, 305, 450, 46] in ModelOrdery
		enum ColumnID implements Tables.AbstractGetValueTableModel.ColumnIDTypeInt<Station>
		{
			Index ("#"   ,    Integer.class,  30, null),
			Name  ("Name",     String.class, 300, station -> station.name),
			URL   ("URL" ,     String.class, 450, station -> station.url ),
			Type  ("Type", SourceType.class,  50, station -> station.type),
			;
			private final Tables.SimplifiedColumnConfig cfg;
			private final Function<Station, ?> getValue;
			
			<V> ColumnID(String name, Class<V> columnClass, int width, Function<Station,V> getValue)
			{
				this.cfg = new Tables.SimplifiedColumnConfig(name, columnClass, 20, -1, width, width);
				this.getValue = getValue;
			}
		
			@Override public Tables.SimplifiedColumnConfig getColumnConfig() { return cfg; }
			@Override public Function<Station, ?> getGetValue() { return getValue; }
			
		}

		StationListTableModel()
		{
			super(ColumnID.values(), knownStations.stationList, ColumnID.Index);
		}

		@Override
		public void setDefaultCellEditorsAndRenderers()
		{
			super.setDefaultCellEditorsAndRenderers();
			
			setCellEditor(ColumnID.Type, new Tables.ComboboxCellEditor<>(SourceType.values()));
			
			StationListTableCellRenderer tcr = new StationListTableCellRenderer();
			for (ColumnID columnID : ColumnID.values())
				switch (columnID)
				{
				case Index:
					break;
				case Name: case URL: case Type:
					setCellRenderer(columnID, tcr);
					break;
				}
		}
		
		private class StationListTableCellRenderer implements TableCellRenderer
		{
			private final Tables.LabelRendererComponent comp;
			
			StationListTableCellRenderer()
			{
				this.comp = new Tables.LabelRendererComponent();
			}

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowV, int columnV)
			{
				int rowM    = rowV   <0 ? -1 : table.convertRowIndexToModel(rowV);
				int columnM = columnV<0 ? -1 : table.convertColumnIndexToModel(columnV);
				ColumnID columnID = getColumnID(columnM);
				boolean isLastRow = rowM+1 == getRowCount();
				
				String valueStr = value==null ? "" : value.toString();
				boolean isCellEditable = isCellEditable(rowM, columnM, columnID);
				Supplier<Color> getCustomBackground = isCellEditable && value==null && !isLastRow ? ()->Color.RED : null;
				comp.configureAsTableCellRendererComponent(table, null, valueStr, isSelected, hasFocus, getCustomBackground, null);
				
				int alignment = SwingConstants.LEFT;
				if (columnID == ColumnID.Type)
					alignment = SwingConstants.CENTER;
				else if (columnID!=null && Number.class.isAssignableFrom(columnID.cfg.columnClass))
					alignment = SwingConstants.RIGHT;
				comp.setHorizontalAlignment(alignment);
				
				return comp;
			}
		}

		@Override
		protected boolean isColumnEditable(ColumnID columnID)
		{
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
			if (rowIndex+1 == getRowCount())
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

	private abstract class AbstractTablePanel<TableModelType extends AbstractTableModel<?,?>> extends JPanel
	{
		private static final long serialVersionUID = 817077438923563653L;
		
		protected final JTable table;
		protected final TableModelType tableModel;
		protected final JToolBar toolBar;
		
		protected AbstractTablePanel(String borderTitle, Supplier<TableModelType> tableModelConstructor)
		{
			super(new BorderLayout());
			setBorder(BorderFactory.createTitledBorder("Ignored Stream URLs"));
			
			tableModel = tableModelConstructor.get();
			table = new JTable(tableModel);
			tableModel.setTable(table);
			table.setRowSorter(new Tables.SimplifiedRowSorter(tableModel));
			table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
			table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
			tableModel.setColumnWidths(table);
			tableModel.setDefaultCellEditorsAndRenderers();
			table.getSelectionModel().addListSelectionListener(ev -> onRowSelect());
			
			JScrollPane tableScrollPane = new JScrollPane(table);
			
			toolBar = new JToolBar();
			toolBar.setFloatable(false);
			
			add(toolBar, BorderLayout.PAGE_START);
			add(tableScrollPane, BorderLayout.CENTER);
		}
		
		protected void onRowSelect()
		{
			updateGuiAccess();
		}
		
		void resetChangesFlag()
		{
			tableModel.setHasChanges(false);
		}
	
		boolean hasChanges()
		{
			return tableModel.hasChanges();
		}
	
		@Override
		public void setEnabled(boolean enabled)
		{
			super.setEnabled(enabled);
			tableModel.setEnabled(enabled);
		}
		
		abstract void updateTable();
		abstract boolean canBeEnabled(Commands command);
	}

	private abstract class AbstractTableModel<ValueType, ColumnIDType extends Tables.AbstractGetValueTableModel.ColumnIDTypeInt<ValueType>>
			extends Tables.SimpleGetValueTableModel<ValueType, ColumnIDType>
	{
		protected boolean hasChanges;
		protected boolean enabled;
		private final ColumnIDType indexColumnID;
	
		protected AbstractTableModel(ColumnIDType[] columns, Vector<ValueType> data, ColumnIDType indexColumnID)
		{
			super(columns, data);
			this.indexColumnID = indexColumnID;
			hasChanges = false;
			enabled = true;
		}
		
		void setEnabled(boolean enabled) { this.enabled = enabled; }
		void setHasChanges(boolean hasChanges) { this.hasChanges = hasChanges; }
		boolean hasChanges() { return hasChanges; }
		
		@Override
		public void setDefaultCellEditorsAndRenderers()
		{
			super.setDefaultCellEditorsAndRenderers();
			setCellRenderer(indexColumnID, new IndexColumnCellRenderer());
		}
	
		protected class IndexColumnCellRenderer implements TableCellRenderer
		{
			private final Tables.LabelRendererComponent comp;
			
			IndexColumnCellRenderer()
			{
				this.comp = new Tables.LabelRendererComponent();
			}
	
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowV, int columnV)
			{
				int rowM    = rowV   <0 ? -1 : table.convertRowIndexToModel(rowV);
				int columnM = columnV<0 ? -1 : table.convertColumnIndexToModel(columnV);
				ColumnIDType columnID = getColumnID(columnM);
				boolean isLastRow = rowM == AbstractTableModel.super.getRowCount();
				
				String valueStr = value==null ? "" : value.toString();
				if (isLastRow && columnID==indexColumnID)
					valueStr = "+";
				
				comp.configureAsTableCellRendererComponent(table, null, valueStr, isSelected, hasFocus);
				comp.setHorizontalAlignment(columnID == indexColumnID ? SwingConstants.CENTER : SwingConstants.LEFT);
				
				return comp;
			}
		}
	
		@Override
		public int getRowCount()
		{
			return super.getRowCount() + 1;
		}
	
		@Override
		public Object getValueAt(int rowIndex, int columnIndex, ColumnIDType columnID)
		{
			if (columnID == indexColumnID) return rowIndex+1;
			return super.getValueAt(rowIndex, columnIndex, columnID);
		}
	
		@Override
		protected boolean isCellEditable(int rowIndex, int columnIndex, ColumnIDType columnID)
		{
			if (!enabled)
				return false;
			
			if (columnID == null)
				return false;
			
			return isColumnEditable(columnID);
		}
	
		protected abstract boolean isColumnEditable(ColumnIDType columnID);
	}
}
