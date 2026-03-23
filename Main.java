import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import org.xml.sax.SAXException;
import org.xml.sax.InputSource;

/**
 * Main application class. Handles command loop and file I/O.
 */
public class Main {
    private static CollectionManager collectionManager;
    private static String fileName;
    private static final int MAX_SCRIPT_DEPTH = 10;
    private static int scriptDepth = 0;

    public static void main(String[] args) {
        // Get file name from environment variable
        fileName = System.getenv("FILE_NAME");
if (fileName == null || fileName.trim().isEmpty()) {
    fileName = "data.xml";
    System.out.println("FILE_NAME not set. Using default: " + fileName);
}

        collectionManager = new CollectionManager();
        loadCollectionFromFile();
        interactiveMode();
    }

    private static void loadCollectionFromFile() {
        File file = new File(fileName);
        if (!file.exists()) {
            System.out.println("File not found. Starting with empty collection.");
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            List<Person> persons = XMLParser.readPersons(reader);
            for (Person p : persons) {
                collectionManager.addPerson(p);
            }
            System.out.println("Collection loaded from file.");
        } catch (IOException | ParserConfigurationException | SAXException | TransformerException e) {
            System.err.println("Error loading collection: " + e.getMessage());
        }
    }

    private static void saveCollectionToFile() {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), "UTF-8"))) {
            XMLParser.writePersons(writer, collectionManager.getAllPersons());
            System.out.println("Collection saved to file.");
        } catch (IOException | TransformerException | ParserConfigurationException e) {
            System.err.println("Error saving collection: " + e.getMessage());
        }
    }

    private static void interactiveMode() {
        try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in, "UTF-8"))) {
            System.out.println("Enter 'help' for list of commands.");
            while (true) {
                System.out.print("> ");
                String line = consoleReader.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;
                processCommand(line, consoleReader);
            }
        } catch (IOException e) {
            System.err.println("Input error: " + e.getMessage());
        }
    }

    private static void processCommand(String command, BufferedReader input) {
        String[] parts = command.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();

        try {
            switch (cmd) {
                case "help":
                    printHelp();
                    break;
                case "info":
                    System.out.println(collectionManager.getInfo());
                    break;
                case "show":
                    collectionManager.show();
                    break;
                case "add":
                    addElement(input);
                    break;
                case "update":
                    if (parts.length < 2) throw new IllegalArgumentException("Missing id.");
                    String[] updParts = parts[1].split("\\s+", 2);
                    if (!updParts[0].equals("id")) throw new IllegalArgumentException("Format: update id <id>");
                    int updId = Integer.parseInt(updParts[1]);
                    updateElement(updId, input);
                    break;
                case "remove_by_id":
                    if (parts.length < 2) throw new IllegalArgumentException("Missing id.");
                    int remId = Integer.parseInt(parts[1]);
                    collectionManager.removeById(remId);
                    break;
                case "clear":
                    collectionManager.clear();
                    break;
                case "save":
                    saveCollectionToFile();
                    break;
                case "execute_script":
                    if (parts.length < 2) throw new IllegalArgumentException("Missing file name.");
                    executeScript(parts[1]);
                    break;
                case "exit":
                    System.out.println("Exiting.");
                    System.exit(0);
                    break;
                case "add_if_min":
                    addIfMin(input);
                    break;
                case "remove_greater":
                    removeGreater(input);
                    break;
                case "remove_lower":
                    removeLower(input);
                    break;
                case "filter_starts_with_name":
                    if (parts.length < 2) throw new IllegalArgumentException("Missing substring.");
                    collectionManager.filterStartsWithName(parts[1]);
                    break;
                case "print_ascending":
                    collectionManager.printAscending();
                    break;
                case "print_descending":
                    collectionManager.printDescending();
                    break;
                default:
                    System.out.println("Unknown command. Type 'help' for list.");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void printHelp() {
        System.out.println("Available commands:");
        System.out.println("  help - show this help");
        System.out.println("  info - information about the collection");
        System.out.println("  show - display all elements");
        System.out.println("  add - add a new element (fields entered line by line)");
        System.out.println("  update id <id> - update element with given id");
        System.out.println("  remove_by_id <id> - remove element by id");
        System.out.println("  clear - clear collection");
        System.out.println("  save - save collection to file");
        System.out.println("  execute_script <file> - execute script from file");
        System.out.println("  exit - exit program (without saving)");
        System.out.println("  add_if_min - add element if it is less than the smallest");
        System.out.println("  remove_greater - remove all elements greater than given");
        System.out.println("  remove_lower - remove all elements lower than given");
        System.out.println("  filter_starts_with_name <name> - show elements whose name starts with substring");
        System.out.println("  print_ascending - show elements in ascending order");
        System.out.println("  print_descending - show elements in descending order");
    }

    private static void addElement(BufferedReader input) {
        Person p = readPerson(input, true, -1);
        if (p != null) {
            collectionManager.addPerson(p);
            System.out.println("Element added. ID: " + p.getId());
        }
    }

    private static void updateElement(int id, BufferedReader input) {
        Person old = collectionManager.getPersonById(id);
        if (old == null) {
            System.out.println("Element with id " + id + " not found.");
            return;
        }
        Person updated = readPerson(input, true, id);
        if (updated != null) {
            updated.setId(old.getId());
            updated.setCreationDate(old.getCreationDate());
            collectionManager.updatePerson(id, updated);
            System.out.println("Element updated.");
        }
    }

    private static void addIfMin(BufferedReader input) {
        Person p = readPerson(input, true, -1);
        if (p != null && collectionManager.isLessThanMin(p)) {
            collectionManager.addPerson(p);
            System.out.println("Element added as minimum.");
        } else {
            System.out.println("Element not added: it is not less than the minimum.");
        }
    }

    private static void removeGreater(BufferedReader input) {
        Person p = readPerson(input, true, -1);
        if (p != null) {
            int removed = collectionManager.removeGreater(p);
            System.out.println("Removed elements: " + removed);
        }
    }

    private static void removeLower(BufferedReader input) {
        Person p = readPerson(input, true, -1);
        if (p != null) {
            int removed = collectionManager.removeLower(p);
            System.out.println("Removed elements: " + removed);
        }
    }

    private static void executeScript(String scriptFileName) {
        if (scriptDepth >= MAX_SCRIPT_DEPTH) {
            System.err.println("Maximum script recursion depth exceeded.");
            return;
        }
        scriptDepth++;
        try (BufferedReader scriptReader = new BufferedReader(new InputStreamReader(new FileInputStream(scriptFileName), "UTF-8"))) {
            String line;
            while ((line = scriptReader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                processCommand(line, scriptReader);
            }
        } catch (IOException e) {
            System.err.println("Error reading script: " + e.getMessage());
        } finally {
            scriptDepth--;
        }
    }

    private static Person readPerson(BufferedReader input, boolean interactive, int existingId) {
        try {
            String name = readString(input, "name", interactive, true);
            if (name == null) return null;
            Coordinates coords = readCoordinates(input, interactive);
            if (coords == null) return null;
            float height = readFloat(input, "height", interactive, true, 0, Float.MAX_VALUE);
            if (height == -1) return null;
            LocalDateTime birthday = readLocalDateTime(input, "birthday", interactive, false);
            Color hairColor = readEnum(input, Color.class, "hairColor", interactive, false);
            Country nationality = readEnum(input, Country.class, "nationality", interactive, false);
            Location location = readLocation(input, interactive, false);
            return new Person(name, coords, height, birthday, hairColor, nationality, location);
        } catch (IOException e) {
            System.err.println("Input error: " + e.getMessage());
            return null;
        }
    }

    private static String readString(BufferedReader input, String fieldName, boolean interactive, boolean notEmpty) throws IOException {
        while (true) {
            if (interactive) System.out.print("Enter " + fieldName + ": ");
            String line = input.readLine();
            if (line == null) return null;
            line = line.trim();
            if (notEmpty && line.isEmpty()) {
                System.out.println("Error: field cannot be empty.");
                continue;
            }
            return line.isEmpty() ? null : line;
        }
    }

    private static float readFloat(BufferedReader input, String fieldName, boolean interactive, boolean notNull, float min, float max) throws IOException {
        while (true) {
            if (interactive) System.out.print("Enter " + fieldName + " (" + min + " - " + max + "): ");
            String line = input.readLine();
            if (line == null) return -1;
            line = line.trim();
            if (line.isEmpty()) {
                if (notNull) {
                    System.out.println("Error: field cannot be empty.");
                    continue;
                } else return -1;
            }
            try {
                float val = Float.parseFloat(line);
                if (val > min && val <= max) return val;
                else System.out.println("Error: value must be in range (" + min + ", " + max + "]");
            } catch (NumberFormatException e) {
                System.out.println("Error: enter a number.");
            }
        }
    }

    private static LocalDateTime readLocalDateTime(BufferedReader input, String fieldName, boolean interactive, boolean notNull) throws IOException {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        while (true) {
            if (interactive) System.out.print("Enter " + fieldName + " (ISO-8601, e.g., 2007-12-03T10:15:30): ");
            String line = input.readLine();
            if (line == null) return null;
            line = line.trim();
            if (line.isEmpty()) {
                if (notNull) {
                    System.out.println("Error: field cannot be empty.");
                    continue;
                } else return null;
            }
            try {
                return LocalDateTime.parse(line, formatter);
            } catch (Exception e) {
                System.out.println("Error: invalid date format.");
            }
        }
    }

    private static <T extends Enum<T>> T readEnum(BufferedReader input, Class<T> enumClass, String fieldName, boolean interactive, boolean notNull) throws IOException {
        T[] constants = enumClass.getEnumConstants();
        while (true) {
            if (interactive) {
                System.out.print("Enter " + fieldName + " (available values: ");
                for (int i = 0; i < constants.length; i++) {
                    if (i > 0) System.out.print(", ");
                    System.out.print(constants[i].name());
                }
                System.out.print("): ");
            }
            String line = input.readLine();
            if (line == null) return null;
            line = line.trim();
            if (line.isEmpty()) {
                if (notNull) {
                    System.out.println("Error: field cannot be empty.");
                    continue;
                } else return null;
            }
            try {
                return Enum.valueOf(enumClass, line);
            } catch (IllegalArgumentException e) {
                System.out.println("Error: invalid value. Allowed: " + Arrays.toString(constants));
            }
        }
    }

    private static Coordinates readCoordinates(BufferedReader input, boolean interactive) throws IOException {
        if (interactive) System.out.println("Enter coordinates:");
        long x = 0;
        while (true) {
            if (interactive) System.out.print("  x (long): ");
            String line = input.readLine();
            if (line == null) return null;
            line = line.trim();
            try {
                x = Long.parseLong(line);
                break;
            } catch (NumberFormatException e) {
                System.out.println("Error: enter an integer.");
            }
        }
        double y = 0;
        while (true) {
            if (interactive) System.out.print("  y (double, max 663): ");
            String line = input.readLine();
            if (line == null) return null;
            line = line.trim();
            try {
                y = Double.parseDouble(line);
                if (y <= 663) break;
                else System.out.println("Error: y cannot exceed 663.");
            } catch (NumberFormatException e) {
                System.out.println("Error: enter a number.");
            }
        }
        return new Coordinates(x, y);
    }

    private static Location readLocation(BufferedReader input, boolean interactive, boolean notNull) throws IOException {
        if (interactive) System.out.println("Enter location (empty line = null):");
        String firstLine = input.readLine();
        if (firstLine == null) return null;
        if (firstLine.trim().isEmpty()) {
            if (notNull) {
                System.out.println("Error: field cannot be null.");
                return null;
            }
            return null;
        }
        double x;
        while (true) {
            if (interactive) System.out.print("  x (double): ");
            String line = input.readLine();
            if (line == null) return null;
            line = line.trim();
            try {
                x = Double.parseDouble(line);
                break;
            } catch (NumberFormatException e) {
                System.out.println("Error: enter a number.");
            }
        }
        Double y;
        while (true) {
            if (interactive) System.out.print("  y (Double, cannot be null): ");
            String line = input.readLine();
            if (line == null) return null;
            line = line.trim();
            if (line.isEmpty()) {
                System.out.println("Error: field cannot be null.");
                continue;
            }
            try {
                y = Double.parseDouble(line);
                break;
            } catch (NumberFormatException e) {
                System.out.println("Error: enter a number.");
            }
        }
        Integer z;
        while (true) {
            if (interactive) System.out.print("  z (Integer, cannot be null): ");
            String line = input.readLine();
            if (line == null) return null;
            line = line.trim();
            if (line.isEmpty()) {
                System.out.println("Error: field cannot be null.");
                continue;
            }
            try {
                z = Integer.parseInt(line);
                break;
            } catch (NumberFormatException e) {
                System.out.println("Error: enter an integer.");
            }
        }
        return new Location(x, y, z);
    }
}

/**
 * Manager for the Person collection.
 */
class CollectionManager {
    private final Map<Integer, Person> collection = new HashMap<>();
    private final Date initDate = new Date();

    public String getInfo() {
        return "Type: HashMap<Person>\nInitialization date: " + initDate + "\nNumber of elements: " + collection.size();
    }

    public void show() {
        if (collection.isEmpty()) {
            System.out.println("Collection is empty.");
            return;
        }
        collection.values().forEach(System.out::println);
    }

    public void addPerson(Person p) {
        if (p.getId() == 0) p.generateId();
        collection.put(p.getId(), p);
    }

    public Person getPersonById(int id) {
        return collection.get(id);
    }

    public void updatePerson(int id, Person newPerson) {
        if (collection.containsKey(id)) {
            newPerson.setId(id);
            collection.put(id, newPerson);
        }
    }

    public void removeById(int id) {
        if (collection.remove(id) != null) System.out.println("Element removed.");
        else System.out.println("Element not found.");
    }

    public void clear() {
        collection.clear();
        System.out.println("Collection cleared.");
    }

    public Collection<Person> getAllPersons() {
        return collection.values();
    }

    public boolean isLessThanMin(Person p) {
        return collection.values().stream().min(Person::compareTo).map(min -> p.compareTo(min) < 0).orElse(true);
    }

    public int removeGreater(Person p) {
        List<Integer> toRemove = new ArrayList<>();
        for (Person person : collection.values()) {
            if (person.compareTo(p) > 0) toRemove.add(person.getId());
        }
        toRemove.forEach(collection::remove);
        return toRemove.size();
    }

    public int removeLower(Person p) {
        List<Integer> toRemove = new ArrayList<>();
        for (Person person : collection.values()) {
            if (person.compareTo(p) < 0) toRemove.add(person.getId());
        }
        toRemove.forEach(collection::remove);
        return toRemove.size();
    }

    public void filterStartsWithName(String prefix) {
        boolean found = false;
        for (Person p : collection.values()) {
            if (p.getName().startsWith(prefix)) {
                System.out.println(p);
                found = true;
            }
        }
        if (!found) System.out.println("No elements found.");
    }

    public void printAscending() {
        collection.values().stream().sorted().forEach(System.out::println);
    }

    public void printDescending() {
        collection.values().stream().sorted(Comparator.reverseOrder()).forEach(System.out::println);
    }
}

/**
 * Person class.
 */
class Person implements Comparable<Person> {
    private int id;
    private String name;
    private Coordinates coordinates;
    private Date creationDate;
    private float height;
    private LocalDateTime birthday;
    private Color hairColor;
    private Country nationality;
    private Location location;

    private static int nextId = 1;

    public Person(String name, Coordinates coordinates, float height, LocalDateTime birthday,
                  Color hairColor, Country nationality, Location location) {
        this.id = 0;
        this.name = name;
        this.coordinates = coordinates;
        this.creationDate = new Date();
        this.height = height;
        this.birthday = birthday;
        this.hairColor = hairColor;
        this.nationality = nationality;
        this.location = location;
        validate();
    }

    public Person(int id, String name, Coordinates coordinates, Date creationDate, float height,
                  LocalDateTime birthday, Color hairColor, Country nationality, Location location) {
        this.id = id;
        this.name = name;
        this.coordinates = coordinates;
        this.creationDate = creationDate;
        this.height = height;
        this.birthday = birthday;
        this.hairColor = hairColor;
        this.nationality = nationality;
        this.location = location;
        validate();
    }

    private void validate() {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Name cannot be empty.");
        if (coordinates == null) throw new IllegalArgumentException("Coordinates cannot be null.");
        if (creationDate == null) throw new IllegalArgumentException("Creation date cannot be null.");
        if (height <= 0) throw new IllegalArgumentException("Height must be greater than 0.");
    }

    public void generateId() {
        if (id == 0) id = nextId++;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public Coordinates getCoordinates() { return coordinates; }
    public Date getCreationDate() { return creationDate; }
    public void setCreationDate(Date date) { this.creationDate = date; }
    public float getHeight() { return height; }
    public LocalDateTime getBirthday() { return birthday; }
    public Color getHairColor() { return hairColor; }
    public Country getNationality() { return nationality; }
    public Location getLocation() { return location; }

    @Override
    public int compareTo(Person o) {
        int cmp = this.name.compareTo(o.name);
        if (cmp != 0) return cmp;
        cmp = Float.compare(this.height, o.height);
        if (cmp != 0) return cmp;
        return Integer.compare(this.id, o.id);
    }

    @Override
    public String toString() {
        return "Person{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", coordinates=" + coordinates +
                ", creationDate=" + creationDate +
                ", height=" + height +
                ", birthday=" + birthday +
                ", hairColor=" + hairColor +
                ", nationality=" + nationality +
                ", location=" + location +
                '}';
    }
}

/**
 * Coordinates class.
 */
class Coordinates {
    private long x;
    private double y; // max 663

    public Coordinates(long x, double y) {
        this.x = x;
        setY(y);
    }

    public long getX() { return x; }
    public double getY() { return y; }
    public void setY(double y) {
        if (y > 663) throw new IllegalArgumentException("y cannot exceed 663");
        this.y = y;
    }

    @Override
    public String toString() {
        return "Coordinates{x=" + x + ", y=" + y + "}";
    }
}

/**
 * Location class.
 */
class Location {
    private double x;
    private Double y; // not null
    private Integer z; // not null

    public Location(double x, Double y, Integer z) {
        this.x = x;
        if (y == null) throw new IllegalArgumentException("y cannot be null");
        if (z == null) throw new IllegalArgumentException("z cannot be null");
        this.y = y;
        this.z = z;
    }

    public double getX() { return x; }
    public Double getY() { return y; }
    public Integer getZ() { return z; }

    @Override
    public String toString() {
        return "Location{x=" + x + ", y=" + y + ", z=" + z + "}";
    }
}

/**
 * Hair color enum.
 */
enum Color {
    GREEN, YELLOW, BROWN
}

/**
 * Nationality enum.
 */
enum Country {
    RUSSIA, UNITED_KINGDOM, FRANCE, SOUTH_KOREA
}

/**
 * XML parser helper.
 */
class XMLParser {
    public static List<Person> readPersons(BufferedReader reader) throws ParserConfigurationException, IOException, SAXException, TransformerException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(reader));
        doc.getDocumentElement().normalize();

        NodeList personNodes = doc.getElementsByTagName("person");
        List<Person> persons = new ArrayList<>();
        for (int i = 0; i < personNodes.getLength(); i++) {
            Element personElem = (Element) personNodes.item(i);
            try {
                int id = Integer.parseInt(getTagValue("id", personElem));
                String name = getTagValue("name", personElem);
                Coordinates coords = readCoordinates(getChildElement(personElem, "coordinates"));
                Date creationDate = java.sql.Date.valueOf(getTagValue("creationDate", personElem));
                float height = Float.parseFloat(getTagValue("height", personElem));
                LocalDateTime birthday = null;
                Element bdayElem = getChildElement(personElem, "birthday");
                if (bdayElem != null) birthday = LocalDateTime.parse(bdayElem.getTextContent(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                Color hairColor = null;
                Element hairElem = getChildElement(personElem, "hairColor");
                if (hairElem != null) hairColor = Color.valueOf(hairElem.getTextContent());
                Country nationality = null;
                Element natElem = getChildElement(personElem, "nationality");
                if (natElem != null) nationality = Country.valueOf(natElem.getTextContent());
                Location location = null;
                Element locElem = getChildElement(personElem, "location");
                if (locElem != null) location = readLocation(locElem);

                Person p = new Person(id, name, coords, creationDate, height, birthday, hairColor, nationality, location);
                persons.add(p);
            } catch (Exception e) {
                System.err.println("Error parsing person element: " + e.getMessage());
            }
        }
        return persons;
    }

    public static void writePersons(BufferedWriter writer, Collection<Person> persons) throws ParserConfigurationException, TransformerException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();
        Element root = doc.createElement("persons");
        doc.appendChild(root);

        for (Person p : persons) {
            Element personElem = doc.createElement("person");
            root.appendChild(personElem);

            addElement(doc, personElem, "id", String.valueOf(p.getId()));
            addElement(doc, personElem, "name", p.getName());
            Element coordsElem = doc.createElement("coordinates");
            personElem.appendChild(coordsElem);
            addElement(doc, coordsElem, "x", String.valueOf(p.getCoordinates().getX()));
            addElement(doc, coordsElem, "y", String.valueOf(p.getCoordinates().getY()));
            addElement(doc, personElem, "creationDate", p.getCreationDate().toString());
            addElement(doc, personElem, "height", String.valueOf(p.getHeight()));
            if (p.getBirthday() != null) addElement(doc, personElem, "birthday", p.getBirthday().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            if (p.getHairColor() != null) addElement(doc, personElem, "hairColor", p.getHairColor().name());
            if (p.getNationality() != null) addElement(doc, personElem, "nationality", p.getNationality().name());
            if (p.getLocation() != null) {
                Element locElem = doc.createElement("location");
                personElem.appendChild(locElem);
                addElement(doc, locElem, "x", String.valueOf(p.getLocation().getX()));
                addElement(doc, locElem, "y", String.valueOf(p.getLocation().getY()));
                addElement(doc, locElem, "z", String.valueOf(p.getLocation().getZ()));
            }
        }

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(writer);
        transformer.transform(source, result);
    }

    private static String getTagValue(String tag, Element element) {
        NodeList list = element.getElementsByTagName(tag);
        if (list.getLength() == 0) return null;
        Node node = list.item(0);
        return node.getTextContent();
    }

    private static Element getChildElement(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) return null;
        return (Element) list.item(0);
    }

    private static Coordinates readCoordinates(Element coordsElem) {
        long x = Long.parseLong(getTagValue("x", coordsElem));
        double y = Double.parseDouble(getTagValue("y", coordsElem));
        return new Coordinates(x, y);
    }

    private static Location readLocation(Element locElem) {
        double x = Double.parseDouble(getTagValue("x", locElem));
        Double y = Double.parseDouble(getTagValue("y", locElem));
        Integer z = Integer.parseInt(getTagValue("z", locElem));
        return new Location(x, y, z);
    }

    private static void addElement(Document doc, Element parent, String tag, String value) {
        Element elem = doc.createElement(tag);
        elem.appendChild(doc.createTextNode(value));
        parent.appendChild(elem);
    }
}