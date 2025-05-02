package net.schwarzbaer.java.tools.livestreamlistconverter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import java.util.Arrays;
import java.util.Vector;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;

import net.schwarzbaer.java.lib.gui.Disabler;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.gui.Tables.LabelRendererComponent;
import net.schwarzbaer.java.lib.gui.Tables.SimplifiedColumnConfig;

class StationListTable extends JPanel
{
	private static final long serialVersionUID = -6522689930616803901L;
	
	private final StationList stationList;
	private final Disabler<Commands> disabler;
	private final JTable table;
	private final StationListTableModel tableModel;

	StationListTable(Window parent, StationList stationList, Runnable readStationList)
	{
		super(new BorderLayout());
		this.stationList = stationList;
		
		disabler = new Disabler<>();
		disabler.setCareFor(Commands.values());
		
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
		toolBar.add(addCommand(Commands.ReadList , LiveStreamListConverter.createButton("Read List from File", GrayCommandIcons.IconGroup.Folder, e -> {
			readStationList.run();
		})));
		toolBar.add(addCommand(Commands.WriteList, LiveStreamListConverter.createButton("Write List to File", GrayCommandIcons.IconGroup.Save, e -> {
			stationList.writeToFile();
		})));
		//toolBar.addSeparator();
		//toolBar.add(LiveStreamListConverter.createButton("Show Column Widths", e -> System.out.printf("ColumnWidths: %sy%n", StationListTableModel.getColumnWidthsAsString(table))));
		toolBar.addSeparator();
		toolBar.add(addCommand(Commands.Delete, LiveStreamListConverter.createButton("Delete Selected", GrayCommandIcons.IconGroup.Delete, e -> {
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
				Station station = stationList.getStation(rowM);
				String name = station==null ? null : station.name;
				if (name==null) name = "<unnamed station>";
				msg.add("[%d] %s".formatted(rowM+1, name));
			}
			int result = JOptionPane.showConfirmDialog(parent, msg.toArray(String[]::new), "Delete Stations", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (result == JOptionPane.YES_OPTION)
			{
				stationList.deleteStations(selectedRowsM);
				tableModel.fireTableUpdate();
				tableModel.notifyAboutChanges();
				updateGuiAccess();
			}
		})));
		toolBar.add(addCommand(Commands.MoveUp  , LiveStreamListConverter.createButton("Move Up"  , GrayCommandIcons.IconGroup.Up  , e -> moveUpDown(stationList::decreaseIndexOfStation))));
		toolBar.add(addCommand(Commands.MoveDown, LiveStreamListConverter.createButton("Move Down", GrayCommandIcons.IconGroup.Down, e -> moveUpDown(stationList::increaseIndexOfStation))));
		
		add(toolBar, BorderLayout.PAGE_START);
		add(tableScrollPane, BorderLayout.CENTER);
	}

	private void moveUpDown(Function<Integer, Integer> moveFcn)
	{
		if (table.getSelectedRowCount() != 1) return;
		int rowV = table.getSelectedRow();
		int rowM = rowV<0 ? -1 : table.convertRowIndexToModel(rowV);
		if (rowM<0) return;
		Integer newIndex = moveFcn.apply( rowM );
		if (newIndex!=null)
		{
			tableModel.fireTableUpdate();
			tableModel.notifyAboutChanges();
			updateGuiAccess();
			table.setRowSelectionInterval(newIndex.intValue(), newIndex.intValue());
		}
	}

	enum Commands { ReadList, WriteList, Delete, MoveUp, MoveDown }
	
	private <C extends JComponent> C addCommand(Commands com, C comp)
	{
		disabler.add(com, comp);
		return comp;
	}
	
	@Override
	public void setEnabled(boolean enabled)
	{
		super.setEnabled(enabled);
		tableModel.setEnabled(enabled);
		disabler.setEnable(command ->
			switch (command)
			{
			case ReadList         -> enabled;
			case WriteList        -> enabled && tableModel.hasChanges();
			case Delete           -> enabled && table.getSelectedRowCount() > 0;
			case MoveDown, MoveUp -> enabled && table.getSelectedRowCount() == 1;
			}
		);
	}
	
	private void updateGuiAccess()
	{
		setEnabled(isEnabled());
	}

	void updateTable()
	{
		tableModel.fireTableUpdate();
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
			super(ColumnID.values(), stationList.stationList);
			hasChanges = false;
			enabled = true;
		}

		void setEnabled(boolean enabled) { this.enabled = enabled; }
		void notifyAboutChanges() { hasChanges = true; }
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
				row = stationList.addNewStation();
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
