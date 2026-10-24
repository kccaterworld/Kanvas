package util;

import game.entities.Enemy;
import java.util.List;

// Java that depends on a .kvs class (Java -> .kvs).
public class Scores {
    public static int totalHp(List<Enemy> enemies) {
        int total = 0;
        for (Enemy enemy : enemies) total += enemy.hp;
        return total;
    }
}
