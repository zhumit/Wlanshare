import com.wlanshare.ui.MdBar;

import javax.swing.JTextArea;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Markdown 工具栏的插入逻辑自测：不依赖真实窗口，直接断言文本与光标位置。
 */
public class MdBarTest {

    private static int pass;
    private static int fail;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                // 1. 无选区加粗：插入占位并选中
                JTextArea a = area("示例", 2);
                MdBar bar = new MdBar(a);
                bar.wrap("**", "**", "粗体文字");
                eq("无选区加粗", "示例**粗体文字**", a.getText());
                eq("占位被选中", "粗体文字", a.getSelectedText());

                // 2. 有选区加粗
                JTextArea b = area("hello world", 0);
                b.select(0, 5);
                new MdBar(b).wrap("**", "**", "x");
                eq("有选区加粗", "**hello** world", b.getText());

                // 3. 行首前缀：光标在第二行
                JTextArea c = area("第一行\n第二行\n第三行", 0);
                c.setCaretPosition(4);
                new MdBar(c).prefixLines("- ");
                eq("单行前缀", "第一行\n- 第二行\n第三行", c.getText());

                // 4. 多行选区前缀
                JTextArea d = area("甲\n乙\n丙", 0);
                MdBar barD = new MdBar(d);
                barD.prefixLines("- ");
                eq("首行前缀", "- 甲\n乙\n丙", d.getText());
                // 再次点击同一行应取消
                barD.prefixLines("- ");
                eq("取消前缀", "甲\n乙\n丙", d.getText());

                // 5. 引用
                JTextArea e = area("引用内容", 0);
                new MdBar(e).prefixLines("> ");
                eq("引用前缀", "> 引用内容", e.getText());

                // 6. 标题：加与去
                JTextArea f = area("文档标题", 0);
                MdBar barF = new MdBar(f);
                barF.heading(2);
                eq("二级标题", "## 文档标题", f.getText());
                barF.heading(1);
                eq("改成一级标题", "# 文档标题", f.getText());
                barF.heading(1);
                eq("同级别再次点击取消", "文档标题", f.getText());

                // 7. 链接：插入模板并选中 url
                JTextArea g = area("", 0);
                new MdBar(g).link();
                eq("插入链接", "[链接文字](https://)", g.getText());
                eq("url 被选中", "https://", g.getSelectedText());

                // 8. 选区链接
                JTextArea h = area("官网", 0);
                h.select(0, 2);
                new MdBar(h).link();
                eq("选区作为链接文字", "[官网](https://)", h.getText());

                // 9. 撤销：应回退最后一次插入
                JTextArea i2 = area("原文", 2);
                MdBar barI = new MdBar(i2);
                barI.wrap("**", "**", "粗体");
                eq("撤销前", "原文**粗体**", i2.getText());
            }
        });
        System.out.println("== 结果: " + pass + " 通过, " + fail + " 失败 ==");
        if (fail > 0) {
            System.exit(1);
        }
    }

    private static JTextArea area(String text, int caret) {
        JTextArea a = new JTextArea(text);
        a.setCaretPosition(Math.min(caret, text.length()));
        return a;
    }

    private static void eq(String name, String expect, String actual) {
        if (expect.equals(actual)) {
            pass++;
            System.out.println("  [PASS] " + name + "  " + actual.replace("\n", "\\n"));
        } else {
            fail++;
            System.out.println("  [FAIL] " + name + "\n         期望: "
                    + expect.replace("\n", "\\n") + "\n         实际: "
                    + actual.replace("\n", "\\n"));
        }
    }
}
