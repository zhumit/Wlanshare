package com.wlanshare.ui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/**
 * 插入表格的行列选择器：点「▦」后在按钮下方弹出网格，
 * 鼠标划过即高亮前 N 行 M 列，点一下插入对应大小的表格（Esc / 点空白关闭）。
 */
public final class TablePicker {

    /** 网格上限。 */
    public static final int MAX = 10;

    private static final int CELL = 15;
    private static final int GAP = 4;

    public interface Pick {
        void onPick(int rows, int cols);
    }

    private TablePicker() {
    }

    /** 在 anchor 下方弹出选择器；选中后回调 (rows, cols)，rows 含表头。 */
    public static void show(JComponent anchor, Pick pick) {
        final JPopupMenu menu = new JPopupMenu();
        menu.setBorder(BorderFactory.createEmptyBorder());
        menu.setOpaque(false);
        JPanel card = card(pick, () -> menu.setVisible(false));
        menu.add(card);
        menu.show(anchor, 0, anchor.getHeight() + 6);
    }

    /** 选择器卡片本体（独立出来便于测试与复用）。 */
    public static JPanel card(final Pick pick, final Runnable close) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(12, 12, 10, 12)));

        final JLabel title = Theme.label("插入表格", 12, Theme.TEXT, true);
        card.add(title, BorderLayout.NORTH);

        final Grid grid = new Grid(new Pick() {
            public void onPick(int rows, int cols) {
                close.run();
                pick.onPick(rows, cols);
            }
        }, title);
        card.add(grid, BorderLayout.CENTER);

        JLabel hint = Theme.label("最多 " + MAX + " × " + MAX + "，行数含表头", 10, Theme.MUTED, false);
        card.add(hint, BorderLayout.SOUTH);
        return card;
    }

    /** 方格网格：悬停高亮 + 点击回调。 */
    private static final class Grid extends JComponent {
        private final Pick pick;
        private final JLabel title;
        private int hoverRow;
        private int hoverCol;

        Grid(Pick pick, JLabel title) {
            this.pick = pick;
            this.title = title;
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    update(e);
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    hoverRow = 0;
                    hoverCol = 0;
                    updateTitle();
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    update(e);
                    if (hoverRow > 0 && hoverCol > 0) {
                        Grid.this.pick.onPick(hoverRow, hoverCol);
                    }
                }
            });
        }

        private void update(MouseEvent e) {
            int col = (e.getX() - 1) / (CELL + GAP) + 1;
            int row = (e.getY() - 1) / (CELL + GAP) + 1;
            col = Math.max(1, Math.min(MAX, col));
            row = Math.max(1, Math.min(MAX, row));
            if (row != hoverRow || col != hoverCol) {
                hoverRow = row;
                hoverCol = col;
                updateTitle();
                repaint();
            }
        }

        private void updateTitle() {
            if (title == null) {
                return;
            }
            title.setText(hoverRow == 0 ? "插入表格" : "插入 " + hoverRow + " 行 × " + hoverCol + " 列");
            title.setForeground(hoverRow == 0 ? Theme.TEXT : Theme.ACCENT);
        }

        @Override
        public Dimension getPreferredSize() {
            int w = MAX * CELL + (MAX - 1) * GAP + 2;
            return new Dimension(w, w);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            for (int r = 1; r <= MAX; r++) {
                for (int c = 1; c <= MAX; c++) {
                    int x = 1 + (c - 1) * (CELL + GAP);
                    int y = 1 + (r - 1) * (CELL + GAP);
                    boolean inRange = r <= hoverRow && c <= hoverCol;
                    g2.setColor(inRange ? new Color(0xDBE6FF) : new Color(0xEFF3FA));
                    Theme.roundRect(g2, x, y, CELL, CELL, 4);
                    if (r == hoverRow && c == hoverCol) {
                        g2.setColor(Theme.ACCENT);
                        g2.setStroke(new BasicStroke(1.6f));
                        Theme.border(g2, x, y, CELL, CELL, 4);
                    }
                }
            }
            g2.dispose();
        }
    }

}
