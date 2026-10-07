import java.util.ArrayList;
import java.util.List;

/** B-tree (minimum degree T) mapping int booking ID -> value. */
public class BTree<V> {

    private static final int T = 3;

    /**
     * Static nested Node class.
     * Values are stored as Object and cast back to V when needed.
     */
    private static class Node {
        int n;
        int[] keys = new int[2 * T - 1];
        Object[] vals = new Object[2 * T - 1];
        Node[] ch = new Node[2 * T];
        boolean leaf = true;
    }

    private Node root = new Node();

    public void insert(int k, V v) {
        if (root.n == 2 * T - 1) {
            Node s = new Node();
            s.leaf = false;
            s.ch[0] = root;

            splitChild(s, 0);
            root = s;
        }

        insertNonFull(root, k, v);
    }

    private void splitChild(Node x, int i) {

        Node y = x.ch[i];
        Node z = new Node();

        z.leaf = y.leaf;
        z.n = T - 1;

        // Copy keys and values
        for (int j = 0; j < T - 1; j++) {
            z.keys[j] = y.keys[j + T];
            z.vals[j] = y.vals[j + T];
        }

        // Copy child references
        if (!y.leaf) {
            for (int j = 0; j < T; j++) {
                z.ch[j] = y.ch[j + T];
            }
        }

        // Reduce number of keys in original node
        y.n = T - 1;

        // Move parent's children
        for (int j = x.n; j >= i + 1; j--) {
            x.ch[j + 1] = x.ch[j];
        }

        x.ch[i + 1] = z;

        // Move parent's keys and values
        for (int j = x.n - 1; j >= i; j--) {
            x.keys[j + 1] = x.keys[j];
            x.vals[j + 1] = x.vals[j];
        }

        // Move middle key to parent
        x.keys[i] = y.keys[T - 1];
        x.vals[i] = y.vals[T - 1];

        x.n++;
    }

    private void insertNonFull(Node x, int k, V v) {

        int i = x.n - 1;

        if (x.leaf) {

            // Move larger keys to the right
            while (i >= 0 && k < x.keys[i]) {
                x.keys[i + 1] = x.keys[i];
                x.vals[i + 1] = x.vals[i];
                i--;
            }

            // Insert new key/value
            x.keys[i + 1] = k;
            x.vals[i + 1] = v;
            x.n++;

        } else {

            // Find appropriate child
            while (i >= 0 && k < x.keys[i]) {
                i--;
            }

            i++;

            // Split child if full
            if (x.ch[i].n == 2 * T - 1) {

                splitChild(x, i);

                if (k > x.keys[i]) {
                    i++;
                }
            }

            insertNonFull(x.ch[i], k, v);
        }
    }

    /**
     * Search for a booking ID.
     */
    @SuppressWarnings("unchecked")
    public V search(int k) {
        return search(root, k);
    }

    @SuppressWarnings("unchecked")
    private V search(Node x, int k) {

        int i = 0;

        while (i < x.n && k > x.keys[i]) {
            i++;
        }

        if (i < x.n && k == x.keys[i]) {
            return (V) x.vals[i];
        }

        if (x.leaf) {
            return null;
        }

        return search(x.ch[i], k);
    }

    /**
     * Returns all values in key order.
     */
    public List<V> inOrder() {

        List<V> out = new ArrayList<>();

        walk(root, out);

        return out;
    }

    @SuppressWarnings("unchecked")
    private void walk(Node x, List<V> out) {

        for (int i = 0; i < x.n; i++) {

            if (!x.leaf) {
                walk(x.ch[i], out);
            }

            out.add((V) x.vals[i]);
        }

        if (!x.leaf) {
            walk(x.ch[x.n], out);
        }
    }
}