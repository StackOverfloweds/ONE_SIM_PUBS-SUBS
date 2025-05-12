/*
 * Copyright 2025 HaiPigGi / StackOverfloweds
 * Released under GPLv3. See LICENSE.txt for details.
 */
package movement;

import core.Coord;
import core.Settings;

/**
 * Random Static movement model
 * Each node is placed randomly within the world size but remains stationary.
 */
public class RandomStatic extends MovementModel {

    private Coord position; // Random initial position

    public RandomStatic(Settings settings) {
        super(settings);
    }

    private RandomStatic(RandomStatic rsm) {
        super(rsm);
        this.position = rsm.position;
    }

    @Override
    public Coord getInitialLocation() {
        assert rng != null : "MovementModel not initialized!";
        double x = rng.nextDouble() * getMaxX();
        double y = rng.nextDouble() * getMaxY();

        this.position = new Coord(x, y);
        return position;
    }

    @Override
    public Path getPath() {
        Path p = new Path(0); // No movement
        p.addWaypoint(position);
        return p;
    }

    @Override
    public double nextPathAvailable() {
        return Double.MAX_VALUE; // No movement, no next path available
    }

    @Override
    public RandomStatic replicate() {
        return new RandomStatic(this);
    }
}
