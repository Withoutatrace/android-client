package com.podevs.android.poAndroid.pokeinfo;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.LruCache;
import android.util.SparseArray;
import com.podevs.android.poAndroid.poke.Gen;
import com.podevs.android.poAndroid.poke.Poke;
import com.podevs.android.poAndroid.poke.ShallowBattlePoke;
import com.podevs.android.poAndroid.poke.PokeEnums.Gender;
import com.podevs.android.poAndroid.poke.UniqueID;
import com.podevs.android.poAndroid.pokeinfo.InfoFiller.Filler;
import com.podevs.android.poAndroid.pokeinfo.InfoFiller.FillerByte;
import com.podevs.android.poAndroid.pokeinfo.StatsInfo.Stats;

import java.util.*;

public class PokemonInfo {
	private static HashMap<String, UniqueID> namesToIds = null;
	private static SparseArray<String> pokeNames = null;
	private static SparseArray<PokeGenData> pokemons[] = null;
	private static SparseArray<PokeData> pokemonsg = null;
	private static int pokeCount = 0;
	private static int pokeCountg[] = null;
	private static LruCache<String, Drawable> ImageCache = new LruCache<String, Drawable>(40);

	/* Data depending on a gen:
	 * ability, type, evolutions, ...
	 */
	private static class PokeGenData {
		byte type1 = -1;
		byte type2 = -1;
		short ability[] = null;
		short moves[] = null;
		String moveString = null;
		byte stats[] = null;
	}

	/* Global data:
	 * base stats, weight, height, ...
	 */
	private static class PokeData {
		byte maxForme = 0;
		byte gender = -1;
		String options = null;
	}

	public static int indexOf(String pokemonName) {
		loadPokeNames();
		for (int i = 0; i < pokeNames.size(); i ++) {
			if (pokeNames.get(pokeNames.keyAt(i)).equals(pokemonName)) {
				return pokeNames.keyAt(i);
			} // pokeNames has random offsets, does it even need these?
		}
		return 0;
	}

	public static String name(UniqueID uID) {
		int num = uID.hashCode();

		loadPokeNames();

		return pokeNames.get(num);
	}

	public static boolean hasVisibleFormes(UniqueID uID) {
		loadPokeNames();

		int num = uID.originalHashCode();
		PokeData data = pokemonsg.get(num);

		//Check the pokemon has formes, and that they are not hidden
		return data.maxForme > 0 && (data.options == null || data.options.indexOf('H') < 0);
	}

	public static List<UniqueID> formes(UniqueID uniqueID, Gen gen) {
		loadPokeNames();
		testLoad(gen.num);

		ArrayList<UniqueID> ret = new ArrayList<UniqueID>();
		PokeData data = pokemonsg.get(uniqueID.originalHashCode());

		for (int i = 0; i <= data.maxForme; i++) {
			UniqueID generated = new UniqueID(uniqueID.pokeNum, i);
			if (exists(generated, gen)) {
				ret.add(generated);
			}
		}

		return ret;
	}

	public static boolean exists (UniqueID uID) {
		loadPokeNames();

		return pokeNames.get(uID.hashCode()) != null;
	}

	public static int numberOfPokemons(Gen gen) {
		testLoad(gen.num);
		return pokeCountg[gen.num];
	}

	public static boolean exists (UniqueID uID, Gen gen) {
		testLoad(gen.num);

		return exists(uID) && pokemons[gen.num].get(uID.hashCode()) != null;
	}

	public static int type1(UniqueID uID, int gen) {
		testLoad(gen);

		int type = -1;
		try {
			type = pokemons[gen].get(uID.hashCode()).type1;
			if (type == -1) {
				type = pokemons[gen].get(uID.originalHashCode()).type1;
			}
		} catch (NullPointerException e) {
			type = pokemons[gen].get((new UniqueID()).hashCode()).type1;
			Log.e("PokemonInfo", "NULL original uID:" + uID);
		}
		return type;
	}

	public static int type2(UniqueID uID, int gen) {
		testLoad(gen);

		int type = -1;
		try {
			type = pokemons[gen].get(uID.hashCode()).type2;
			if (type == -1) {
				type = pokemons[gen].get(uID.originalHashCode()).type2;
			}
		} catch (NullPointerException e) {
			type = pokemons[gen].get((new UniqueID()).hashCode()).type2;
			Log.e("PokemonInfo", "NULL original uID:" + uID);
		}
		return type;
	}

	public static int stat(UniqueID uId, int stat, int gen) {
		loadStats(gen);
		byte stats[] = pokemons[gen].get(uId.hashCode()).stats;

		if (stats == null) {
			stats = pokemons[gen].get(uId.originalHashCode()).stats;
		}
		return (stats[stat] + 256) % 256;
	}

	public static short[] moves(UniqueID uId, int gen, int sub) {
		testLoadMoves(gen, sub);
		convertMoveStringIfNeeded(uId, gen);

		if (pokemons[gen].get(uId.hashCode()).moves == null && uId.subNum != 0) {
			return moves(uId.original(), gen, sub);
		}

		short ret[] = pokemons[gen].get(uId.hashCode()).moves;

		if (ret == null) {
			return new short[0];
		} else {
			return ret;
		}
	}

	private static void convertMoveStringIfNeeded(UniqueID uId, int gen) {
		PokeGenData poke = pokemons[gen].get(uId.hashCode());
		if (poke.moveString != null) {
			String moves[] = poke.moveString.split(" ");

			Arrays.sort(moves, new Comparator<String>() {
				public int compare(String lhs, String rhs) {
					return MoveInfo.name(Integer.parseInt(lhs)).compareTo(MoveInfo.name(Integer.parseInt(rhs)));
				}
			});

			poke.moves = new short[moves.length];

			for (int i = 0; i < moves.length; i++) {
				poke.moves[i] = (short)Integer.parseInt(moves[i]);
			}
			poke.moveString = null;
		}
	}

	private static void testLoadMoves(final int gen, final int sub) {
		testLoad(gen);
		if (pokemons[gen].get(1).moveString != null || pokemons[gen].get(1).moves != null) {
			return;
		}
		String test;
		if (gen == 6 && sub == 1) {
			test = "db/pokes/6G/Subgen 1/all_moves.txt";
			} else {
			test = "db/pokes/" + gen + "G/all_moves.txt";
		}
		/*
		String path = "db/pokes/" + gen + "G/Subgen " + sub + "all_moves.txt";

		 */
		InfoFiller.uIDfill(test, new Filler() {
			public void fill(int i, String s) {
				PokeGenData poke = pokemons[gen].get(i);
				if (poke != null) {
					pokemons[gen].get(i).moveString = s;
				}
			}
		});
	}

	public static void resetGen6() {
		pokemons[6] = null;
	}

	public static int calcStat(Poke poke, int stat, int gen) {
		if (stat == Stats.Hp.ordinal()) {
			return ((2*stat(poke.uID(), stat, gen) + poke.dv(stat) * (1 + (gen <= 2 ? 1 : 0) ) + poke.ev(stat)/4)*poke.level())/100 + 5 + 5 + poke.level();
		} else {
			int base = ((2*stat(poke.uID(), stat, gen) + poke.dv(stat) * (1 + (gen <= 2 ? 1 : 0) ) + poke.ev(stat)/4)*poke.level())/100 + 5;

			return NatureInfo.boostStat(base, poke.nature(), stat);
		}
	}

	public static int calcMinMaxStat(UniqueID ID, int stat, int gen, int level, int minmax) {
		if (stat == 0) {
			return (int) Math.floor(((2*stat(ID, stat, gen) + (minmax == 1 ? 31 : 0) * (1 + (gen <= 2 ? 1 : 0) ) + (minmax == 1 ? 252 : 0)/4)*level)/100 + 5 + 5 + level);
		} else {
			double base = Math.floor(((2*stat(ID, stat, gen) + (minmax == 1 ? 31 : 0) * (1 + (gen <= 2 ? 1 : 0) ) + (minmax == 1 ? 252 : 0)/4)*level)/100 + 5);
			return (int) Math.floor(base + (minmax == 1 ? 1 : -1)*(base/10));
		}
	}



	private static void loadStats(final int gen) {
		testLoad(gen);
		if (pokemons[gen].get(1).stats != null) {
			return;
		}
		InfoFiller.uIDfill("db/pokes/" + gen + "G/stats.txt", new Filler() {
			public void fill(int i, String s) {
				byte [] stats = new byte[6];
				int curIndex = 0;
				
				/* faster than using a split (using test project to measure times) */
				for (int j = 0; j < 6; j++) {
					int nextIndex = s.indexOf(' ', curIndex);
					stats[j] = (byte)Integer.parseInt(s.substring(curIndex, nextIndex == - 1 ? s.length() : nextIndex));
					curIndex = nextIndex+1;
				}

				pokemons[gen].get(i).stats = stats;
			}
		});
	}

	private static int iconRes(UniqueID uid) {
		Resources resources = InfoConfig.resources;
		int resID = resources.getIdentifier("pi_" + uid.pokeNum +
				(uid.subNum == 0 ? "" : "_" + uid.subNum), "drawable", InfoConfig.pkgName);
		if (resID == 0)
			resID = resources.getIdentifier("pi_" + uid.pokeNum, "drawable", InfoConfig.pkgName);
		return resID;
	}

	public static Drawable iconDrawable(UniqueID uid) {
		try {
			return InfoConfig.resources.getDrawable(iconRes(uid));
		} catch (Resources.NotFoundException e) {
			return InfoConfig.resources.getDrawable(iconRes(uid.original()));
		} catch (NullPointerException e) {
			Log.e("PokemonInfo", "NULL ICON" + uid);
			return InfoConfig.resources.getDrawable(iconRes(new UniqueID()));
		}
	}

	public static Drawable iconDrawablePokeballStatus() {
		String iconKey = "pi_status";
		Drawable drawable = ImageCache.get(iconKey);
		if (drawable == null) {
			int i = InfoConfig.resources.getIdentifier(iconKey, "drawable", InfoConfig.pkgName);
			drawable = InfoConfig.resources.getDrawable(i);
			ImageCache.put(iconKey, drawable);
		}
		return drawable;
	}

	public static Drawable iconDrawableCache(UniqueID uid) {
		String iconKey = "pi"+uid.pokeNum+"-"+uid.subNum;
		Drawable drawable = ImageCache.get(iconKey);
		if (drawable == null) {
			drawable = iconDrawable(uid);
			ImageCache.put(iconKey, drawable);
		}
		return drawable;
	}

	private static Drawable itemDrawable(String itemId) {
		Resources resource = InfoConfig.resources;
		Integer identifier = resource.getIdentifier(itemId, "drawable", InfoConfig.pkgName);
		return resource.getDrawable(identifier);
	}

	public static Drawable itemDrawableCache(Integer itemId) {
		String itemKey = "i" + itemId.toString();
		Drawable drawable =  ImageCache.get(itemKey);
		if (drawable == null) {
			drawable = itemDrawable(itemKey);
			ImageCache.put(itemKey, drawable);
		}
		return drawable;
	}

	private static Drawable genderDrawable(String key) {
		Resources resource = InfoConfig.resources;
		Integer identifier = resource.getIdentifier(key, "drawable", InfoConfig.pkgName);
		return resource.getDrawable(identifier);
	}

	public static Drawable genderDrawableCache(Integer gender) {
		String genderKey = "battle_gender" + gender.toString();
		Drawable drawable = ImageCache.get(genderKey);
		if (drawable == null) {
			drawable = genderDrawable(genderKey);
			ImageCache.put(genderKey, drawable);
		}
		return drawable;
	}

	public static String sprite(ShallowBattlePoke poke, boolean front) {
		String res;
		UniqueID uID;
		if (poke.specialSprites.isEmpty()) {
			uID = poke.uID;
		} else {
			uID = poke.specialSprites.peek();
		}
		if (uID.pokeNum < 0) {
			res = "empty_sprite";
		} else {
			res = "p" + uID.pokeNum + (uID.subNum == 0 ? "" : "_" + uID.subNum) +
					(front ? "_front" : "_back");
			if (poke.gender == Gender.Female.ordinal()) {
				if (InfoConfig.resources.getIdentifier(res + "f", "drawable", InfoConfig.pkgName) != 0 && poke.gender == Gender.Female.ordinal())
					// Special female sprite
					res = res + "f";
			}
			if (poke.shiny) {
				if (InfoConfig.resources.getIdentifier(res + "s", "drawable", InfoConfig.pkgName) != 0) {
					res += (poke.shiny ? "s" : "");
				}
			}
		}
		int ident = InfoConfig.resources.getIdentifier(res, "drawable", InfoConfig.pkgName);
		if (ident == 0) {
			// No sprite found. Default to missingNo.
			if (front) {
				return "missingno.png";
			} else {
				return sprite(poke, true);
			}
		} else {
			return res + ".png";
		}
	}

	public static String cacheStatus() {
		return ImageCache.toString();
	}

	@SuppressWarnings("unchecked")
	private static void testLoad(int gen) {
		if (pokemons == null) {
			pokemons = new SparseArray[GenInfo.genMax()+1];
		}
		if (pokemons[gen] == null) {
			pokemons[gen] = new SparseArray<PokemonInfo.PokeGenData>();
		}
		if (pokeCountg == null) {
			pokeCountg = new int[GenInfo.genMax()+1];
		}
		if (pokemons[gen].indexOfKey(1) == (byte)-1) {
			loadTypes(gen);
		}
	}

	private static void loadPokeNames() {
		if (pokeNames != null) {
			return;
		}
		pokeNames = new SparseArray<String>();
		pokemonsg = new SparseArray<PokemonInfo.PokeData>();
		namesToIds = new HashMap<String, UniqueID>();
		InfoFiller.uIDfill("db/pokes/pokemons.txt", new InfoFiller.OptionsFiller() {
			public void fill(int i, String s, String options) {
				pokeNames.put(i, s);
				pokemonsg.put(i, new PokeData());

				if (i > 16000) {
					pokemonsg.get(i % 65536).maxForme = (byte) (i >> 16);
				} else if (i > pokeCount) {
					pokeCount = i;
				}
				pokemonsg.get(i).options = options;
				namesToIds.put(s, new UniqueID(i));
			}
		});
	}

	private static void loadTypes(final int gen) {
		pokeCountg[gen] = 0;
		/* First load all the released pokemon and prepare the "data containers" */
		InfoFiller.uIDfill("db/pokes/" + gen  + "G/released.txt", new Filler() {
			public void fill(int i, String s) {
				pokemons[gen].put(i, new PokeGenData());
				if (i < 16000 && i > pokeCountg[gen]) {
					pokeCountg[gen] = i;
				}
			}
		}, true);
		InfoFiller.uIDfill("db/pokes/" + gen  + "G/type1.txt", new FillerByte() {
			@Override
			void fillByte(int i, byte b) {
                try {
                    pokemons[gen].get(i).type1 = b;
                } catch (Exception e) {
                    Log.e("PokemonInfo", "Impossible to load type 1 for gen " + gen + ", poke: " + i);
                }
            }
		});
		InfoFiller.uIDfill("db/pokes/" + gen  + "G/type2.txt", new FillerByte() {
			@Override
			void fillByte(int i, byte b) {
				pokemons[gen].get(i).type2 = b;
			}
		});
	}

	/*
	public static int numberOfPokemons() {
		loadPokeNames();

		return pokeCount;
	}

	public static int totalNumberOfPokemons() {
		loadPokeNames();

		return pokeNames.size();
	}
	*/

	public static String[] nameArray(Gen gen) {
		String ret[] = new String[numberOfPokemons(gen) + 1]; //+1 for missingno

		for (HashMap.Entry<String, UniqueID> entry : namesToIds.entrySet()) {
			if (entry.getValue().subNum == 0 && entry.getValue().pokeNum < ret.length) {
				ret[entry.getValue().pokeNum] = entry.getKey();
			}
		}
		return ret;
	}

	public static short[] abilities(UniqueID id, int gen) {
		testLoadAbilities(id, gen);

		short abilities[] = pokemons[gen].get(id.hashCode()).ability;

		if (abilities == null) {
			abilities = pokemons[gen].get(id.originalHashCode()).ability;
		}
		return abilities;
	}

	private static void testLoadAbilities(UniqueID id, final int gen) {
		if (gen >= 3) {
			testLoad(gen);
			if (pokemons[gen].get(id.originalHashCode()).ability != null) {
				return;
			}

			InfoFiller.uIDfill("db/pokes/" + gen  + "G/ability1.txt", new Filler() {
				public void fill(int i, String s) {
					pokemons[gen].get(i).ability = new short[3];
					pokemons[gen].get(i).ability[0] = Short.parseShort(s);
				}
			});
			InfoFiller.uIDfill("db/pokes/" + gen  + "G/ability2.txt", new Filler() {
				public void fill(int i, String s) {
					pokemons[gen].get(i).ability[1] = Short.parseShort(s);
				}
			});
			InfoFiller.uIDfill("db/pokes/" + gen  + "G/ability3.txt", new Filler() {
				public void fill(int i, String s) {
					pokemons[gen].get(i).ability[2] = Short.parseShort(s);
				}
			});
		}
	}

	public static UniqueID number(String name) {
		return namesToIds.get(name);
	}

	public static int gender(UniqueID uID) {
		testLoadGenders();

        byte gender = pokemonsg.get(uID.hashCode()).gender;
        if (gender == -1) {
            gender = pokemonsg.get(uID.originalHashCode()).gender;
        }

		return gender == -1 ? 0 : gender;
	}

	private static boolean gendersLoaded = false;
	private static void testLoadGenders() {
		if (!gendersLoaded) {
			loadPokeNames();
			gendersLoaded = true;

			InfoFiller.uIDfill("db/pokes/gender.txt", new FillerByte() {
				@Override
				void fillByte(int i, byte b) {
					pokemonsg.get(i).gender = b;
				}
			});
		}
	}
}
