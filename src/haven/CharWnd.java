/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import haven.Text.Foundry;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.awt.font.TextAttribute;
import java.util.*;

public class CharWnd extends Window {
	Widget cattr, skill, belief;
	Worship ancw;
	Label cost, skcost;
	Label explbl;
	int exp;
	int btime = 0;
	SkillList psk, nsk;
	SkillInfo ski;
	public static ArrayList<SCapVal> sCaps = new ArrayList<SCapVal>();
	public static FoodMeter foodm;
	public static Study study;
	static Map<String, Attr> attrs = new TreeMap<String, Attr>();
	public static final Tex missing = Resource.loadtex("gfx/invobjs/missing");
	public static final Tex foodmimg = Resource.loadtex("gfx/hud/charsh/foodm");
	public static final Color debuff = new Color(255, 128, 128);
	public static final Color buff = new Color(128, 255, 128);
	public static final RichText.Foundry skbodfnd = new RichText.Foundry(
			TextAttribute.FAMILY, "SansSerif", TextAttribute.SIZE, 9);
	public static final Tex btimeoff = Resource
			.loadtex("gfx/hud/charsh/shieldgray");
	public static final Tex btimeon = Resource.loadtex("gfx/hud/charsh/shield");
	public static final Tex nmeter = Resource
			.loadtex("gfx/hud/charsh/numenmeter");
	public static final Tex ancestors = Resource
			.loadtex("gfx/hud/charsh/ancestors");

	public HashMap<String, Belief> beliefs = null;

	static {
		Widget.addtype("chr", new WidgetFactory() {
			public Widget create(Coord c, Widget parent, Object[] args) {
				int studyid = -1;
				if (args.length > 0)
					studyid = (Integer) args[0];
				return (new CharWnd(c, parent, studyid));
			}
		});
	}

	class Attr implements Observer {
		String nm;
		Glob.CAttr attr;

		Attr(String nm) {
			this.nm = nm;
			attr = ui.sess.glob.cattr.get(nm);
			attrs.put(nm, this);
			attr.addObserver(this);
		}

		public void update() {
		}

		public void update(Observable attrslen, Object uudata) {
			update();
		}

		private void destroy() {
			attr.deleteObserver(this);
		}
	}

	public class Belief extends Attr {
		boolean inv;
		Img flarper;
		int lx;
		final Tex slider = Resource.loadtex("gfx/hud/charsh/bslider");
		final Tex flarp = Resource.loadtex("gfx/hud/sflarp");
		final IButton lb, rb;
		final BufferedImage lbu = Resource.loadimg("gfx/hud/charsh/leftup");
		final BufferedImage lbd = Resource.loadimg("gfx/hud/charsh/leftdown");
		final BufferedImage lbg = Resource.loadimg("gfx/hud/charsh/leftgrey");
		final BufferedImage rbu = Resource.loadimg("gfx/hud/charsh/rightup");
		final BufferedImage rbd = Resource.loadimg("gfx/hud/charsh/rightdown");
		final BufferedImage rbg = Resource.loadimg("gfx/hud/charsh/rightgrey");

		Belief(String nm, String left, String right, boolean inv, int x, int y) {
			super(nm);
			this.inv = inv;
			lx = x;
			Label lbl = new Label(new Coord(x, y), belief, String.format(
					"%s / %s", Utils.titlecase(left), Utils.titlecase(right)));
			lbl.c = new Coord(72 + x - (lbl.sz.x / 2), y);
			y += 15;
			new Img(new Coord(x, y),
					Resource.loadtex("gfx/hud/charsh/" + left), belief);
			lb = new IButton(new Coord(x + 16, y), belief, lbu, lbd) {
				public void click() {
					buy(-1);
				}
			};
			new Img(new Coord(x + 32, y + 4), slider, belief);
			rb = new IButton(new Coord(x + 112, y), belief, rbu, rbd) {
				public void click() {
					buy(1);
				}
			};
			new Img(new Coord(x + 128, y), Resource.loadtex("gfx/hud/charsh/"
					+ right), belief);
			flarper = new Img(new Coord(0, y + 2), flarp, belief);
			update();
		}

		public int getval() {
			int val = attr.comp;
			if (inv)
				val = -val;
			return val;
		}

		public void buy(int ch) {
			if (inv)
				ch = -ch;
			CharWnd.this.wdgmsg("believe", nm, ch);
		}

		public void update() {
			int val = attr.comp;
			if (inv)
				val = -val;
			flarper.c = new Coord((7 * (val + 5)) + 31 + lx, flarper.c.y);
			if (btime > 0) {
				lb.up = lbg;
				lb.down = lbg;
				rb.up = rbg;
				rb.down = rbg;
			} else {
				lb.up = lbu;
				lb.down = lbd;
				rb.up = rbu;
				rb.down = rbd;
			}
			lb.render();
			rb.render();
		}
	}

	class NAttr extends Attr {
		ArrayList<String> attrNames = new ArrayList<String>(
				Arrays.asList("str", "agil", "intel", "cons", "perc", "csm", "dxt", "psy"));
		Label lbl;
		Label lbl2;

		NAttr(String nm, int x, int y) {
			super(nm);
			this.lbl = new Label(new Coord(x, y), cattr, "");
			if (attrNames.contains(nm))
				this.lbl2 = new Label(new Coord(x+35, y), cattr, "");
			update();
		}

		public void update() {
			if (attrNames.contains(nm) && lbl2 != null) {
				if (attr.comp != attr.base)	{
					lbl2.settext(Integer.toString(attr.comp));
					if (attr.comp < attr.base) {
						// debuff
						lbl2.setcolor(debuff);
						lbl2.tooltip = String.format("%d", attr.comp - attr.base);
					} else if (attr.comp > attr.base) {
						// buff
						lbl2.setcolor(buff);
						lbl2.tooltip = String.format("+%d", attr.comp - attr.base);
					} else {
						lbl2.setcolor(Color.WHITE);
						lbl2.tooltip = null;
					}
				} else {
					lbl2.settext("");
					lbl2.tooltip = null;
				}
			}
			lbl.settext(Integer.toString(attr.base));
			if ((nm == "intel") && study != null) {
				study.setattnlimit(attr.comp);
			}
			for (int i = 0; i < sCaps.size(); i++) {
				SCapVal scap = sCaps.get(i);
				if (scap != null) {
					scap.update();
				}
			}
		}
	}

	private void updexp() {
		int cost = 0;
		for (Attr attr : attrs.values()) {
			if (attr instanceof SAttr)
				cost += ((SAttr) attr).cost;
		}
		this.cost.settext(Integer.toString(cost));
		this.explbl.settext(Integer.toString(exp));
		if (cost > exp)
			this.cost.setcolor(new Color(255, 128, 128));
		else
			this.cost.setcolor(new Color(255, 255, 255));
	}

	class SAttr extends NAttr {
		IButton minus, plus;
		int tvalb, tvalc;
		int cost;

		SAttr(String nm, int x, int y) {
			super(nm, x, y);
			tvalb = attr.base;
			tvalc = attr.comp;
			minus = new IButton(new Coord(x + 30, y), cattr,
					Resource.loadimg("gfx/hud/charsh/minusup"),
					Resource.loadimg("gfx/hud/charsh/minusdown")) {
				public void click() {
					dec();
					upd();
				}

				public boolean mousewheel(Coord c, int a) {
					if (a < 0)
						inc();
					else
						dec();
					upd();
					return (true);
				}
			};
			plus = new IButton(new Coord(x + 45, y), cattr,
					Resource.loadimg("gfx/hud/charsh/plusup"),
					Resource.loadimg("gfx/hud/charsh/plusdown")) {
				public void click() {
					inc();
					upd();
				}

				public boolean mousewheel(Coord c, int a) {
					if (a < 0)
						inc();
					else
						dec();
					upd();
					return (true);
				}
			};
		}

		void upd() {
			lbl.settext(Integer.toString(tvalc));
			if (tvalb > attr.base) {
				lbl.setcolor(new Color(128, 128, 255));
				lbl.tooltip = String.format("%d", attr.base);
			} else if (attr.comp > attr.base) {
				lbl.setcolor(buff);
				lbl.tooltip = String.format("%d + %d", attr.base, attr.comp - attr.base);
			} else if (attr.comp < attr.base) {
				lbl.setcolor(debuff);
				lbl.tooltip = String.format("%d - %d", attr.base, attr.base - attr.comp);
			} else {
				lbl.setcolor(Color.WHITE);
				lbl.tooltip = null;
			}
			updexp();
		}

		boolean inc() {
			int k;
			int n;
			if (ui.modctrl) {
				k = 2 * attr.base + 1;
				n = (int) ((Math.sqrt(k * k + 2 * exp / 25) - k) / 2);
				tvalb = attr.base + n;
				tvalc = attr.comp + n;
				cost = 50 * (k + n) * n;
			} else if (ui.modshift) {
				k = 2 * tvalb + 1;
				n = 10;
				tvalb += n;
				tvalc += n;
				cost += 50 * (k + n) * n;
			} else {
				tvalb++;
				tvalc++;
				cost += tvalb * 100;
			}
			return (true);
		}

		boolean dec() {
			if (tvalb > attr.base) {
				if (ui.modctrl) {
					tvalb = attr.base;
					tvalc = attr.comp;
					cost = 0;
				} else if (ui.modshift) {
					int n = Math.min(10, tvalb - attr.base);
					tvalb -= n;
					tvalc -= n;
					int k = 2 * tvalb + 1;
					cost -= 50 * (k + n) * n;
				} else {
					cost -= tvalb * 100;
					tvalb--;
					tvalc--;
				}
				return (true);
			}
			return (false);
		}

		public void update() {
			super.update();
			tvalb = attr.base;
			tvalc = attr.comp;
			cost = 0;
			upd();
		}
	}

	class SCapVal {
		String[] as;
		boolean sqrt;
		Label baseLbl;
		Label totalLbl;

		public SCapVal(int x, int y, String[] as, boolean sqrt)
		{
			this.as = as;
			this.sqrt = sqrt;

			baseLbl = new Label(new Coord(x += 75, y), cattr, "");
			baseLbl.setcolor(Color.WHITE);

			totalLbl = new Label(new Coord(x += 35, y), cattr, "");
			totalLbl.setcolor(buff);

			update();
		}

		public void update()
		{
			int baseVal = 1;
			int totalVal = 1;
			for (int i = 0; i < as.length; i++)
			{
				// Values
				baseVal *= getStat(as[i]);
				totalVal *= getStatTotal(as[i]);
			}

			if (sqrt) {
				baseVal = (int) Math.round(Math.sqrt(baseVal));
				totalVal = (int) Math.round(Math.sqrt(totalVal));
			}

			baseLbl.settext("" + baseVal);

			// Show total value (if necessary)
			if (baseVal != totalVal)
			{
				totalLbl.settext("" + totalVal);
			}
			else
			{
				totalLbl.settext("");
			}
		}
	}

	private class BTimer extends Widget {
		public BTimer(Coord c, Widget parent) {
			super(c, btimeoff.sz(), parent);
		}

		public void draw(GOut g) {
			if (btime > 0)
				g.image(btimeoff, Coord.z);
			else
				g.image(btimeon, Coord.z);
		}

		public Object tooltip(Coord c, boolean again) {
			int real_btime;
			int m, h;
			double x1;
			if (btime == 0)
				return (null);
			else
				real_btime = (btime - 1) / 3;
			x1 = real_btime / 3600;
			h = (int) x1;
			m = (real_btime - (h * 3600)) / 60;
			return (String.format("%d hours %d min left (%d orig)", h, m,
					(btime - 1) / 60));
		}
	}

	public class FoodMeter extends Widget {
		int cap = 0;
		List<El> els = new LinkedList<El>();

		private class El {
			String id;
			int amount;
			Color col;

			public El(String id, int amount, Color col) {
				this.id = id;
				this.amount = amount;
				this.col = col;
			}
		}

		public FoodMeter(Coord c, Widget parent) {
			super(c, foodmimg.sz(), parent);
		}

		public String[] getElsNames() {
			String[] ret;
			synchronized(els) {
				ret = new String[els.size()];
				for(int i = 0; i < els.size(); ++i) {
					ret[i] = els.get(i).id;
				}
			}
			return ret;
		}

		public double getFepValue(String id) {
			double ret = 0.0;
			synchronized(els) {
				for (int i = 0; i < els.size(); ++i) {
					El tel = els.get(i);
					if (tel.id.equals(id)) {
						ret = ((double) tel.amount)/10;
						break;
					}
				}
			}
			return ret;
		}

		public int getCap() {
			return cap;
		}

		public void draw(GOut g) {
			g.chcolor(Color.BLACK);
			g.frect(new Coord(4, 4), sz.add(-8, -8));
			g.chcolor(255, 255, 255, 128);
			g.image(foodmimg, Coord.z);
			g.chcolor();
			synchronized (els) {
				int x = 4;
				for (El el : els) {
					int w = (174 * el.amount) / cap;
					g.chcolor(el.col);
					g.frect(new Coord(x, 4), new Coord(w, 24));
					x += w;
				}
				g.chcolor();
			}
			g.chcolor(255, 255, 255, 128);
			g.image(foodmimg, Coord.z);
			g.chcolor();
			super.draw(g);
		}

		public void update(Object... args) {
			cap = (Integer) args[0];
			int sum = 0;
			synchronized (els) {
				els.clear();
				for (int i = 1; i < args.length; i += 3) {
					String id = (String) args[i];
					int amount = (Integer) args[i + 1];
					Color col = (Color) args[i + 2];
					els.add(new El(id, amount, col));
					sum += amount;
				}
			}
			if (els.size() == 0) {
				tooltip = String.format("0 of %.1f", cap / 10.0);
			} else {
				String tt = "";
				for (El el : els)
					tt += String.format("%.1f %s + ", el.amount / 10.0, el.id);
				tt = tt.substring(0, tt.length() - 3);
				tooltip = String.format("(%s) = %.1f of %.1f", tt, sum / 10.0,
						cap / 10.0);
			}
		}
	}

	private class SkillInfo extends RichTextBox {
		Resource cur = null;

		public SkillInfo(Coord c, Coord sz, Widget parent) {
			super(c, sz, parent, "", skbodfnd);
		}

		public void draw(GOut g) {
			if ((cur != null) && !cur.loading) {
				StringBuilder text = new StringBuilder();
				text.append("$img[" + cur.name + "]\n\n");
				text.append("$font[serif,16]{" + cur.layer(Resource.tooltip).t
						+ "}\n\n");
				text.append(cur.layer(Resource.pagina).text);
				settext(text.toString());
				cur = null;
			}
			super.draw(g);
		}

		public void setsk(Resource sk) {
			cur = sk;
			settext("");
		}
	}

	private class Worship extends Widget {
		Inventory[] wishes = new Inventory[3];
		Text title, numen;
		Tex img;

		public Worship(Coord c, Widget parent, String title, Tex img) {
			super(c, new Coord(100, 200), parent);
			canhastrash = false;
			this.title = Text.render(title);
			this.img = img;
			this.numen = Text.render("0");
			for (int i = 0; i < wishes.length; i++)
				wishes[i] = new Inventory(new Coord(i * 31, 119), new Coord(1,
						1), this);
			new Button(new Coord(10, 160), 80, this, "Forfeit") {
				public void click() {
					CharWnd.this.wdgmsg("forfeit", 0);
				}
			};
		}

		public void draw(GOut g) {
			g.image(title.tex(), new Coord(50 - (title.tex().sz().x / 2), 0));
			g.image(img, new Coord(50 - (img.sz().x / 2), 15));
			Coord nmc = new Coord(50 - (nmeter.sz().x / 2), 100);
			g.image(nmeter, nmc);
			g.image(numen.tex(), nmc.add(18, 16 - numen.tex().sz().y));
			super.draw(g);
		}

		public void wish(int i, Indir<Resource> res, int amount) {
			wishes[i].unlink();
			wishes[i] = new Inventory(new Coord(i * 31, 119), new Coord(1, 1),
					this);
			new Item(Coord.z, res, -1, wishes[i], null, amount);
		}

		public void numen(int n) {
			this.numen = Text.render(Integer.toString(n));
		}
	}

	private class SkillList extends Widget {
		int h;
		Scrollbar sb;
		int sel;
		List<Resource> skills = new ArrayList<Resource>();
		Map<Resource, Integer> costs = new HashMap<Resource, Integer>();
		Comparator<Resource> rescomp = new Comparator<Resource>() {
			public int compare(Resource a, Resource b) {
				String an, bn;
				if (a.loading)
					an = a.name;
				else
					an = a.layer(Resource.tooltip).t;
				if (b.loading)
					bn = b.name;
				else
					bn = b.layer(Resource.tooltip).t;
				return (an.compareTo(bn));
			}
		};

		public SkillList(Coord c, Coord sz, Widget parent) {
			super(c, sz, parent);
			h = sz.y / 20;
			sel = -1;
			sb = new Scrollbar(new Coord(sz.x, 0), sz.y, this, 0, 4) {
				public void changed() {
				}
			};
		}

		public void draw(GOut g) {
			Collections.sort(skills, rescomp);
			g.chcolor(Color.BLACK);
			g.frect(Coord.z, sz);
			g.chcolor();
			for (int i = 0; i < h; i++) {
				if (i + sb.val >= skills.size())
					continue;
				Resource sk = skills.get(i + sb.val);
				if (i + sb.val == sel) {
					g.chcolor(255, 255, 0, 128);
					g.frect(new Coord(0, i * 20), new Coord(sz.x, 20));
					g.chcolor();
				}
				if (getcost(sk) > exp)
					g.chcolor(255, 128, 128, 255);
				if (sk.loading) {
					g.image(missing, new Coord(0, i * 20), new Coord(20, 20));
					g.atext("...", new Coord(25, i * 20 + 10), 0, 0.5);
					continue;
				}
				g.image(sk.layer(Resource.imgc).tex(), new Coord(0, i * 20),
						new Coord(20, 20));
				g.atext(sk.layer(Resource.tooltip).t,
						new Coord(25, i * 20 + 10), 0, 0.5);
				g.chcolor();
			}
			super.draw(g);
		}

		public void pop(Collection<Resource> nsk) {
			List<Resource> skills = new ArrayList<Resource>();
			for (Resource res : nsk)
				skills.add(res);
			sb.val = 0;
			sb.max = skills.size() - h;
			sel = -1;
			this.skills = skills;
		}

		public boolean mousewheel(Coord c, int amount) {
			sb.ch(amount);
			return (true);
		}

		public int getcost(Resource sk) {
			synchronized (costs) {
				if (costs.get(sk) == null)
					return (0);
				else
					return (costs.get(sk));
			}
		}

		public boolean mousedown(Coord c, int button) {
			if (super.mousedown(c, button))
				return (true);
			if (button == 1) {
				sel = (c.y / 20) + sb.val;
				if (sel >= skills.size())
					sel = -1;
				changed((sel < 0) ? null : skills.get(sel));
				return (true);
			}
			return (false);
		}

		public void changed(Resource sk) {
		}

		public void unsel() {
			sel = -1;
		}
	}

	public class Study extends Widget {
		Label attlbl, lplabel;
		Window wnd;
		boolean svis, attached = true;
		private Coord detsz = new Coord(110, 150);
		private Coord detc = new Coord(-145, -75);
		int attlimit, attused = 0;
		long studylp;

		public Study(Widget parent) {
			super(Coord.z, new Coord(400, 295), parent);
			ui.wnd_study = this;
			Foundry fnd = new Foundry(new Font("SansSerif", Font.PLAIN, 12));
			new Label(new Coord(138, 202), this, "Attention:", fnd);
			new Label(new Coord(138, 222), this, "Study LP:", fnd);
			attlimit = ui.sess.glob.cattr.get("intel").comp;
			attlbl = new Label(new Coord(200, 202), this, "", fnd);
			lplabel = new Label(new Coord(200, 222), this, "", fnd);

			canhastrash = false;
			visible = false;
		}

		private void upd() {
			lplabel.settext(String.valueOf(studylp));
			attlbl.settext(attused + "/" + attlimit);
			attlbl.c.x = 263 - attlbl.sz.x;
		}

		public void toggle() {
			if (wnd == null) {
				wnd = new Window(new Coord(150, 150), detsz, ui.root, "Study") {
					public void destroy() {
						wnd = null;
						if (!attached) {
							Study.this.unlink();
						}
						super.destroy();
					}
				};
				wnd.justclose = true;
				if (!attached) {
					this.visible = svis;
					detach();
				}
				wnd.visible = !attached;
			} else {
				ui.destroy(wnd);
			}
		}

		public void detach() {
			svis = this.visible;
			if (wnd != null) {
				this.unlink();
				this.parent = wnd;
				this.link();
				this.c = detc;
				wnd.show();
				this.visible = true;
			}
			attached = false;
		}

		public void attach() {
			if (wnd != null) {
				wnd.hide();
			}
			this.c = Coord.z;
			this.unlink();
			this.parent = CharWnd.this;
			this.link();
			this.visible = svis;
			attached = true;
		}

		public void setattnlimit(int val) {
			attlimit = val;
			upd();
		}

		public void setattnused(int val) {
			attused = val;
			upd();
		}

		public void updateStudyLp() {
			studylp = 0;
			for (Widget wdg = this.child; wdg != null; wdg = wdg.next) {
				if (wdg instanceof Inventory) {
					Inventory sinv = (Inventory) wdg;
					for (Widget sitem = sinv.child; sitem != null; sitem = sitem.next) {
						if (sitem instanceof Item) {
							Item it = (Item) sitem;
							if (((Item) sitem).curio_stat == null)
								continue;
							studylp += Math.round(it.curio_stat.baseLP * it.qmult * UI.instance.wnd_char.getExpMode());
						}
					}
				}
			}
			upd();
		}
	}

	private void buysattrs() {
		ArrayList<Object> args = new ArrayList<Object>();
		for (Attr attr : attrs.values()) {
			if (attr instanceof SAttr) {
				SAttr sa = (SAttr) attr;
				args.add(sa.nm);
				args.add(sa.tvalb);
			}
		}
		wdgmsg("sattr", args.toArray());
	}

	private void buyskill() {
		if (nsk.sel >= 0)
			wdgmsg("buy", nsk.skills.get(nsk.sel).basename());
	}

	private void baseval(int y, String id, String nm) {
		new Img(new Coord(10, y), Resource.loadtex("gfx/hud/charsh/" + id),
				cattr);
		new Label(new Coord(30, y), cattr, nm + ":");
		new NAttr(id, 100, y);
	}

	private void skillval(int y, String id, String nm) {
		new Img(new Coord(210, y), Resource.loadtex("gfx/hud/charsh/" + id),
				cattr);
		new Label(new Coord(230, y), cattr, nm + ":");
		new SAttr(id, 320, y);
	}

	private SCapVal softcapval(int y, String[] as, String nm, boolean sqrt)
	{
		int x = 25;

		for (int i = 0; i < as.length; i++)
		{
			// Icons
			Img tx = new Img(new Coord(x - (15 * i), y), Resource.loadtex("gfx/hud/charsh/" + as[i]), cattr);
		}

		// Name
		new Label(new Coord(x += 15, y), cattr, nm + ":");

		return new SCapVal(x, y, as, sqrt);
	}

	public CharWnd(Coord c, Widget parent, int studyid) {
		super(c, new Coord(400, 400), parent, "Character Sheet");
		ui.wnd_char = this;
		int y;
		cattr = new Widget(Coord.z, new Coord(400, 300), this);
		new Label(new Coord(10, 10), cattr, "Base Attributes:");
		y = 25;
		baseval(y += 15, "str", "Strength");
		baseval(y += 15, "agil", "Agility");
		baseval(y += 15, "intel", "Intelligence");
		baseval(y += 15, "cons", "Constitution");
		baseval(y += 15, "perc", "Perception");
		baseval(y += 15, "csm", "Charisma");
		baseval(y += 15, "dxt", "Dexterity");
		baseval(y += 15, "psy", "Psyche");
		foodm = new FoodMeter(new Coord(10, 310), cattr);

		int expbase = 220;
		new Label(new Coord(210, expbase), cattr, "Cost:");
		cost = new Label(new Coord(300, expbase), cattr, "0");
		new Label(new Coord(210, expbase + 15), cattr, "Learning Points:");
		explbl = new Label(new Coord(300, expbase + 15), cattr, "0");
		new Label(new Coord(210, expbase + 30), cattr, "Learning Ability:");
		expattr = new NAttr("expmod", 300, expbase + 30) {
			public void update() {
				lbl.settext(String.format("%d%%", attr.comp));
				if (attr.comp < 100)
					lbl.setcolor(debuff);
				else if (attr.comp > 100)
					lbl.setcolor(buff);
				else
					lbl.setcolor(Color.WHITE);
			}
		};
		new Button(new Coord(210, expbase + 45), 75, cattr, "Buy") {
			public void click() {
				buysattrs();
			}
		};

		new Button(new Coord(295, expbase + 45), 75, cattr, "Reset") {
			public void click() {
				for (Attr attr : attrs.values()) {
					if (attr instanceof SAttr)
						((SAttr) attr).update();
				}
				updexp();
			}
		};

		y = 25;
		new Label(new Coord(210, 10), cattr, "Skill Values:");
		skillval(y += 15, "unarmed", "Unarmed Combat");
		skillval(y += 15, "melee", "Melee Combat");
		skillval(y += 15, "ranged", "Marksmanship");
		skillval(y += 15, "explore", "Exploration");
		skillval(y += 15, "stealth", "Stealth");
		skillval(y += 15, "sewing", "Sewing");
		skillval(y += 15, "smithing", "Smithing");
		skillval(y += 15, "carpentry", "Carpentry");
		skillval(y += 15, "cooking", "Cooking");
		skillval(y += 15, "farming", "Farming");
		skillval(y += 15, "survive", "Survival");

		y = 170;
		sCaps = new ArrayList<SCapVal>();
		sCaps.add(softcapval(y, new String[]{"perc", "explore"}, "Sight", false)); // Perc x Exp
		sCaps.add(softcapval(y += 15, new String[]{"perc", "cooking"}, "Baking", true));   // Perc x Cooking
		sCaps.add(softcapval(y += 15, new String[]{"psy", "smithing"}, "Goldsmithing", true)); // Psy x Smith
		sCaps.add(softcapval(y += 15, new String[]{"str", "smithing"}, "Metalworking", true)); // Str x Smith
		sCaps.add(softcapval(y += 15, new String[]{"intel", "stealth"}, "Evasion", true)); // Int x Stealth
		sCaps.add(softcapval(y += 15, new String[]{"dxt", "sewing"}, "Weaving", true)); // Dex x Sewing
		sCaps.add(softcapval(y += 15, new String[]{"psy", "sewing"}, "Psycrafting", true)); // Psy x Sewing

		skill = new Widget(Coord.z, new Coord(400, 275), this);
		ski = new SkillInfo(new Coord(10, 10), new Coord(180, 260), skill);
		new Label(new Coord(210, 10), skill, "Available Skills:");
		nsk = new SkillList(new Coord(210, 25), new Coord(180, 100), skill) {
			public void changed(Resource sk) {
				psk.unsel();
				skcost.settext("Cost: " + nsk.getcost(sk));
				ski.setsk(sk);
			}
		};
		new Button(new Coord(210, 130), 75, skill, "Learn") {
			public void click() {
				buyskill();
			}
		};
		skcost = new Label(new Coord(300, 130), skill, "Cost: N/A");
		new Label(new Coord(210, 155), skill, "Current Skills:");
		psk = new SkillList(new Coord(210, 170), new Coord(180, 100), skill) {
			public void changed(Resource sk) {
				nsk.unsel();
				skcost.settext("Cost: N/A");
				ski.setsk(sk);
			}
		};

		skill.visible = false;

		belief = new Widget(Coord.z, new Coord(400, 275), this);
		new BTimer(new Coord(10, 10), belief);

		beliefs = new HashMap<String, CharWnd.Belief>();
		beliefs.put("life", new Belief("life", "death", "life", false, 18, 50));
		beliefs.put("night", new Belief("night", "night", "day", true, 18, 85));
		beliefs.put("civil", new Belief("civil", "barbarism", "civilization",
				false, 18, 120));
		beliefs.put("nature", new Belief("nature", "nature", "industry", true,
				18, 155));
		beliefs.put("martial", new Belief("martial", "martial", "peaceful",
				true, 18, 190));
		beliefs.put("change", new Belief("change", "tradition", "change",
				false, 18, 225));

		ancw = new Worship(new Coord(255, 40), belief, "The Ancestors",
				ancestors);

		belief.visible = false;

		study = new Study(this);
		if (studyid >= 0)
			ui.bind(study, studyid);

		int bx = 10;
		new IButton(new Coord(245, 310), this,
				Resource.loadimg("gfx/hud/charsh/attribup"),
				Resource.loadimg("gfx/hud/charsh/attribdown")) {
			public void click() {
				cattr.visible = true;
				skill.visible = false;
				belief.visible = false;
				study.visible = false;
			}
		}.tooltip = "Attributes";
		if (studyid >= 0) {
			new IButton(new Coord(245, 350), this,
					Resource.loadimg("gfx/hud/charsh/ideasup"),
					Resource.loadimg("gfx/hud/charsh/ideasdown")) {
				public void click() {
					cattr.visible = false;
					skill.visible = false;
					belief.visible = false;
					study.visible = true;
				}
			}.tooltip = "Study";
		}
		new IButton(new Coord(310, 350), this,
				Resource.loadimg("gfx/hud/charsh/skillsup"),
				Resource.loadimg("gfx/hud/charsh/skillsdown")) {
			public void click() {
				cattr.visible = false;
				skill.visible = true;
				belief.visible = false;
				study.visible = false;
			}
		}.tooltip = "Skills";
		new IButton(new Coord(310, 310), this,
				Resource.loadimg("gfx/hud/charsh/worshipup"),
				Resource.loadimg("gfx/hud/charsh/worshipdown")) {
			public void click() {
				cattr.visible = false;
				skill.visible = false;
				belief.visible = true;
				study.visible = false;
			}
		}.tooltip = "Personal Beliefs";

		hide();
		//Kerri: last created window
		if(Config.toggleCA)
			if(UI.instance.menugrid != null) {
				Resource r = Resource.load("paginae/act/crime");
				UI.instance.menugrid.use(r);
			}
		if(Config.toggleTR)
			if(UI.instance.menugrid != null) {
				Resource r = Resource.load("paginae/act/tracking");
				UI.instance.menugrid.use(r);
			}
		if(Config.new_chat)
			ChatHWPanel.fbtn.click();
		if(Config.toggleCL){
			if(UI.instance.minimappanel != null) {
				UI.instance.minimappanel.vcl.click();
				UI.instance.minimappanel.pcl.click();
			}
		}
	}

	public static int getMaxFepValue() {
		ArrayList<String> attrNames = new ArrayList<String>(
				Arrays.asList("str", "agil", "intel", "cons", "perc", "csm", "dxt", "psy"));
		int ret = 0;
		for (Attr attr : attrs.values()) {
			if (!attrNames.contains(attr.nm))
				continue;
			if (attr.attr.base > ret)
				ret = attr.attr.base;
		}
		return ret;
	}

	public static String getMaxFepName() {
		ArrayList<String> attrNames = new ArrayList<String>(
				Arrays.asList("str", "agil", "intel", "cons", "perc", "csm", "dxt", "psy"));
		String ret = null;
		int m = 0;
		for (Attr attr : attrs.values()) {
			if (!attrNames.contains(attr.nm))
				continue;
			if (attr.attr.base > m) {
				m = attr.attr.base;
				ret = attr.nm;
			}
		}
		return ret;
	}

	public static int getStat(String name) {
		int ret = 0;
		for (Attr attr : attrs.values()) {
			if (attr.attr.nm.equals(name)) {
				ret = attr.attr.base;
			}
		}
		return ret;
	}
	public static int getStatTotal(String name) {
		int ret = 0;
		for (Attr attr : attrs.values()) {
			if (attr.attr.nm.equals(name)) {
				ret = attr.attr.comp;
			}
		}
		return ret;
	}
	public void uimsg(String msg, Object... args) {
		if (msg == "exp") {
			exp = (Integer) args[0];
			updexp();
		} else if (msg == "studynum") {
			study.setattnused((Integer) args[0]);
			study.updateStudyLp();
		} else if (msg == "reset") {
			updexp();
		} else if (msg == "nsk") {
			Collection<Resource> skl = new LinkedList<Resource>();
			for (int i = 0; i < args.length; i += 2) {
				Resource res = Resource.load("gfx/hud/skills/"
						+ (String) args[i]);
				int cost = (Integer) args[i + 1];
				skl.add(res);
				synchronized (nsk.costs) {
					nsk.costs.put(res, cost);
				}
			}
			nsk.pop(skl);
		} else if (msg == "psk") {
			Collection<Resource> skl = new LinkedList<Resource>();
			for (int i = 0; i < args.length; i++) {
				Resource res = Resource.load("gfx/hud/skills/"
						+ (String) args[i]);
				skl.add(res);
			}
			psk.pop(skl);
		} else if (msg == "food") {
			foodm.update(args);
		} else if (msg == "btime") {
			btime = (Integer) args[0];
		} else if (msg == "wish") {
			int ent = (Integer) args[0];
			int wish = (Integer) args[1];
			int resid = (Integer) args[2];
			int amount = (Integer) args[3];
			if (ent == 0)
				ancw.wish(wish, ui.sess.getres(resid), amount);
		} else if (msg == "numen") {
			int ent = (Integer) args[0];
			int numen = (Integer) args[1];
			if (ent == 0)
				ancw.numen(numen);
		}
	}

	@Override
	public void hide() {
		study.detach();
		super.hide();
	}

	@Override
	public void show() {
		study.attach();
		super.show();
	}

	public void toggle() {
		if (visible) {
			hide();
		} else {
			show();
		}
	}

	public boolean type(char key, java.awt.event.KeyEvent ev) {
		if (key == 27) {
			toggle();
			return (true);
		}
		return (super.type(key, ev));
	}

	public void wdgmsg(Widget sender, String msg, Object... args) {
		if (sender == cbtn) {
			hide();
			return;
		}
		if (ui.rwidgets.containsKey(sender)) {
			super.wdgmsg(sender, msg, args);
			return;
		}
		if (sender instanceof Item)
			return;
		if (sender instanceof Inventory)
			return;
		super.wdgmsg(sender, msg, args);
	}

	public void destroy() {
		for (Attr attr : attrs.values())
			attr.destroy();
		super.destroy();
	}

	NAttr expattr = null;
	public double getExpMode() {
		try {
			return (double)expattr.attr.comp / 100;
		} catch (NullPointerException ex) {
			return 1;
		}
	}
}
