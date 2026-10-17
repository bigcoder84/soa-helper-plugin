package cn.bigcoder.soa.helper.ui;

import cn.bigcoder.soa.helper.settings.JumpRoute;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * 路由表格模型（跳转项编辑对话框内使用）
 */
class JumpRoutesTableModel extends AbstractTableModel {

    private final String[] columnNames = {"启用", "名称", "条件", "URL模板"};
    private final List<JumpRoute> routes = new ArrayList<>();

    @Override
    public int getRowCount() {
        return routes.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == 0 ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 0;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        JumpRoute route = routes.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return route.isEnabled();
            case 1:
                return route.getName();
            case 2:
                return singleLine(route.getCondition());
            case 3:
                return route.getUrlTemplate();
            default:
                return null;
        }
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex == 0) {
            routes.get(rowIndex).setEnabled((Boolean) aValue);
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }

    public void addRoute(JumpRoute route) {
        routes.add(route);
        fireTableRowsInserted(routes.size() - 1, routes.size() - 1);
    }

    public void updateRoute(int index, JumpRoute route) {
        routes.set(index, route);
        fireTableRowsUpdated(index, index);
    }

    public void removeRoute(int index) {
        routes.remove(index);
        fireTableRowsDeleted(index, index);
    }

    public void moveUp(int index) {
        if (index > 0) {
            JumpRoute route = routes.remove(index);
            routes.add(index - 1, route);
            fireTableRowsUpdated(index - 1, index);
        }
    }

    public void moveDown(int index) {
        if (index < routes.size() - 1) {
            JumpRoute route = routes.remove(index);
            routes.add(index + 1, route);
            fireTableRowsUpdated(index, index + 1);
        }
    }

    public JumpRoute getRouteAt(int index) {
        return routes.get(index);
    }

    /**
     * 返回路由副本，避免外部修改影响表格
     */
    public List<JumpRoute> getRoutes() {
        List<JumpRoute> copies = new ArrayList<>(routes.size());
        for (JumpRoute route : routes) {
            copies.add(route.copy());
        }
        return copies;
    }

    public void setRoutes(List<JumpRoute> newRoutes) {
        routes.clear();
        for (JumpRoute route : newRoutes) {
            routes.add(route.copy());
        }
        fireTableDataChanged();
    }

    private static String singleLine(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }
}
