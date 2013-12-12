package april.sim;

public interface Shape
{
    /** Return max possible distance from center to facilitate
     * fast-checking collisions**/
    public double getBoundingRadius();

    /** If starting at a vector p and traveling in unit direction
     * dir, when will the first collision occur? Return MAX_VALUE if
     * no collision.
     **/

//    public double collisionRay(double p[], double dir[]);

    /** Does this shape, when transformed by T, collide with shape s?
     * (T serves the same role as OpenGL's model view matrix. **/
//    public boolean collision(Shape s, double T[][]);

}
