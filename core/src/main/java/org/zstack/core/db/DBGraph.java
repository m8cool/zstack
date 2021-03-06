package org.zstack.core.db;

import org.zstack.header.core.StaticInit;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.vo.EntityGraph;
import org.zstack.utils.BeanUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.*;

public class DBGraph {
    private static CLogger logger = Utils.getLogger(DBGraph.class);

    private static Map<Class, Map<Class, Key>> keys = new HashMap<>();
    private static Map<Class, Node> allNodes = new HashMap<>();

    private static final int PARENT_WEIGHT = 1;
    private static final int FRIEND_WEIGHT = 2;

    private static class Key {
        String src;
        String dst;
        int weight;

        @Override
        public String toString() {
            return String.format("<%s, %s, %s>", src, dst, weight);
        }
    }

    private static class Node {
        Class entityClass;
        Set<Node> neighbours = new HashSet<>();

        @Override
        public String toString() {
            return entityClass.getSimpleName();
        }
    }

    public static class EntityVertex {
        public Class entityClass;
        public String srcKey;
        public String dstKey;
        public EntityVertex next;
        public EntityVertex previous;

        @Override
        public String toString() {
            return "EntityVertex{" +
                    "entityClass=" + entityClass +
                    ", srcKey='" + srcKey + '\'' +
                    ", dstKey='" + dstKey + '\'' +
                    ", next=" + next +
                    ", previous=" + (previous == null ? null : previous.entityClass.getSimpleName()) +
                    '}';
        }
    }

    private static Key getKey(Node leftNode, Node rightNode) {
        Map<Class, Key> kmap = keys.get(leftNode.entityClass);
        if (kmap == null) {
            throw new CloudRuntimeException(String.format("cannot find key node[%s] -> node[%s]", leftNode.entityClass, rightNode.entityClass));
        }

        Key key = kmap.get(rightNode.entityClass);
        if (key == null) {
            throw new CloudRuntimeException(String.format("cannot find key node[%s] -> node[%s]", leftNode.entityClass, rightNode.entityClass));
        }

        return key;
    }

    public static boolean isTwoResourcesConnected(Class src, Class dst) {
        return findVerticesWithSmallestWeight(src, dst) != null;
    }

    public static EntityVertex findVerticesWithSmallestWeight(Class src, Class dst) {
        List<List<Node>> all = findPath(src, dst);
        if (all.isEmpty()) {
            return null;
        }

        class Judge {
            EntityVertex vertex;
            int weight;
        }

        List<Judge> judges = new ArrayList<>();
        all.forEach(lst -> {
            EntityVertex vertex = new EntityVertex();
            Judge j = new Judge();
            j.vertex = vertex;
            judges.add(j);

            Node left = lst.get(0);
            Iterator<Node> it = lst.subList(1, lst.size()).iterator();
            vertex.entityClass = left.entityClass;

            EntityVertex current = vertex;
            while (it.hasNext()) {
                Node right = it.next();
                Key key = getKey(left, right);
                j.weight += key.weight;
                current.srcKey = key.src;
                current.dstKey = key.dst;
                current.next = new EntityVertex();
                current.next.entityClass = right.entityClass;
                current.next.previous = current;
                current = current.next;
                left = right;
            }
        });

        Judge smallest = null;
        for (Judge judge : judges) {
            if (smallest == null) {
                smallest = judge;
                continue;
            }

            if (smallest.weight > judge.weight) {
                smallest = judge;
            }
        }

        assert smallest != null;
        return smallest.vertex;
    }

    private static class NoNodeException extends Exception {
        public NoNodeException() {
        }

        public NoNodeException(String message) {
            super(message);
        }

        public NoNodeException(String message, Throwable cause) {
            super(message, cause);
        }

        public NoNodeException(Throwable cause) {
            super(cause);
        }

        public NoNodeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }

    private static List<List<Node>> findPath(Class src, Class dst) {
        class PathFinder {
            List<List<Node>> paths = new ArrayList<>();

            Stack<Node> path = new Stack<>();

            Node node(Class clz) throws NoNodeException {
                Node n = allNodes.get(clz);
                if (n == null) {
                    throw new NoNodeException(String.format("cannot find node for class[%s]", clz));
                }
                return n;
            }

            private void find(Node n) {
                //logger.debug("-----> " + n.toString());
                if (path.contains(n)) {
                    // cycle
                    return;
                }

                path.push(n);

                for (Node nb : n.neighbours) {
                    if (nb.entityClass == dst) {
                        List<Node> ret = new ArrayList<>(path);
                        ret.add(nb);
                        //logger.debug(String.format("11111111111111111111111111 %s", ret));
                        paths.add(ret);
                    } else {
                        find(nb);
                    }
                }

                path.pop();
            }

            List<List<Node>> find() throws NoNodeException {
                find(node(src));
                return paths;
            }
        }

        try {
            return new PathFinder().find();
        } catch (NoNodeException e) {
            return new ArrayList<>();
        }
    }

    @StaticInit
    static void staticInit() {
        class NodeResolver {
            EntityGraph entityGraph;
            Node me;

            public NodeResolver(Class clz) {
                if (allNodes.containsKey(clz)) {
                    me = allNodes.get(clz);
                } else {
                    me = new Node();
                    me.entityClass = clz;
                    allNodes.put(clz, me);

                    entityGraph = (EntityGraph) clz.getAnnotation(EntityGraph.class);

                    if (entityGraph == null) {
                        Class tmp = clz;
                        while (tmp != Object.class) {
                            entityGraph = (EntityGraph) tmp.getAnnotation(EntityGraph.class);
                            if (entityGraph != null) {
                                break;
                            }
                            tmp = tmp.getSuperclass();
                        }

                        if (entityGraph == null) {
                            return;
                        }
                    }

                    resolveNeighbours();
                }
            }

            private void resolveNeighbours() {
                for (EntityGraph.Neighbour at : entityGraph.parents()) {
                    me.neighbours.add(new NodeResolver(at.type()).resolve());
                    buildKey(at, at.weight() == -1 ? PARENT_WEIGHT : at.weight());
                }

                for (EntityGraph.Neighbour fat : entityGraph.friends()) {
                    me.neighbours.add(new NodeResolver(fat.type()).resolve());
                    buildKey(fat, fat.weight() == -1 ? FRIEND_WEIGHT : fat.weight());
                }
            }

            private void buildKey(EntityGraph.Neighbour at, int w) {
                Map<Class, Key> second = keys.computeIfAbsent(me.entityClass, x->new HashMap<>());
                Key key = new Key();
                key.src = at.myField();
                key.dst = at.targetField();
                key.weight = w;
                second.put(at.type(), key);
            }

            Node resolve() {
                return me;
            }
        }

        BeanUtils.reflections.getTypesAnnotatedWith(EntityGraph.class)
                .forEach(clz -> new NodeResolver(clz).resolve());
    }
}
