import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A class to demonstrate a simple client-server connection using sockets.
 *
 */
public class SockServer {
  static Socket sock;
  static DataOutputStream os;
  static ObjectInputStream in;

  static int port = 8888;

  private static List<Product> inventory = new ArrayList<>();
  public static void main (String args[]) {

    if (args.length != 1) {
      System.out.println("Expected arguments: <port(int)>");
      System.exit(1);
    }

    try {
      port = Integer.parseInt(args[0]);
    } catch (NumberFormatException nfe) {
      System.out.println("[Port|sleepDelay] must be an integer");
      System.exit(2);
    }

    try {
      //open socket
      ServerSocket serv = new ServerSocket(port);
      System.out.println("Server ready for connections");

      /**
       * Simple loop accepting one client and calling handling one request.
       *
       */


      while (true){
        System.out.println("Server waiting for a connection");
        sock = serv.accept(); // blocking wait
        System.out.println("Client connected");

        // setup the object reading channel
        in = new ObjectInputStream(sock.getInputStream());

        // get output channel
        OutputStream out = sock.getOutputStream();

        // create an object output writer (Java only)
        os = new DataOutputStream(out);

        boolean connected = true;
        while (connected) {
          String s = "";
          try {
            s = (String) in.readObject(); // attempt to read string in from client
          } catch (Exception e) { // catch rough disconnect
            System.out.println("Client disconnect");
            connected = false;
            continue;
          }

          JSONObject res = isValid(s);

          if (res.has("ok")) {
            writeOut(res);
            continue;
          }

          JSONObject req = new JSONObject(s);

          res = testField(req, "type");
          if (!res.getBoolean("ok")) { // no "type" header provided
            res = noType(req);
            writeOut(res);
            continue;
          }
          // check which request it is (could also be a switch statement)
          if (req.getString("type").equals("echo")) {
            res = echo(req);
          } else if (req.getString("type").equals("add")) {
            res = add(req);
          } else if (req.getString("type").equals("addmany")) {
            res = addmany(req);
          } else if(req.getString("type").equals("inventory")){
            res = inventory(req);
          } else if (req.getString("type").equals("roller")){
            res = roller(req);
          } else {
            res = wrongType(req);
          }
          writeOut(res);
        }
        // if we are here - client has disconnected so close connection to socket
        overandout();
      }
    } catch(Exception e) {
      e.printStackTrace();
      overandout(); // close connection to socket upon error
    }
  }


  /**
   * Checks if a specific field exists
   *
   */
  static JSONObject testField(JSONObject req, String key){
    JSONObject res = new JSONObject();

    // field does not exist
    if (!req.has(key)){
      res.put("ok", false);
      res.put("message", "Field " + key + " does not exist in request");
      return res;
    }
    return res.put("ok", true);
  }

  // handles the simple echo request
  static JSONObject echo(JSONObject req){
    System.out.println("Echo request: " + req.toString());
    JSONObject res = testField(req, "data");
    if (res.getBoolean("ok")) {
      if (!req.get("data").getClass().getName().equals("java.lang.String")){
        res.put("ok", false);
        res.put("message", "Field data needs to be of type: String");
        return res;
      }

      res.put("type", "echo");
      res.put("echo", "Here is your echo: " + req.getString("data"));
    }
    return res;
  }

  // handles the simple add request with two numbers
  static JSONObject add(JSONObject req){
    System.out.println("Add request: " + req.toString());
    JSONObject res1 = testField(req, "num1");
    if (!res1.getBoolean("ok")) {
      return res1;
    }

    JSONObject res2 = testField(req, "num2");
    if (!res2.getBoolean("ok")) {
      return res2;
    }

    JSONObject res = new JSONObject();
    res.put("ok", true);
    res.put("type", "add");
    try {
      res.put("result", req.getInt("num1") + req.getInt("num2"));
    } catch (org.json.JSONException e){
      res.put("ok", false);
      res.put("message", "Field num1/num2 needs to be of type: int");
    }
    return res;
  }

  // implement me in assignment 3
  static JSONObject inventory(JSONObject req) {
    System.out.println("Inventory request: " + req.toString());
    JSONObject res = new JSONObject();
    try{
      String task = req.getString("task");
      if (task.equals("add")){
        String productName = req.getString("productName");
        int quantity = req.getInt("quantity");
        addProduct(productName, quantity);
        res.put("ok", true);
        res.put("message", "Added " + quantity + " " + productName + " to inventory");
        } else if (task.equals("view")){
          JSONArray inventoryJsonArray = new JSONArray();
          for (Product p : inventory){
            JSONObject productJson = new JSONObject();
            productJson.put("productName", p.getName());
            productJson.put("quantity", p.getQuantity());
            inventoryJsonArray.put(productJson);
          }
          res.put("ok", true);
          res.put("inventory", inventoryJsonArray);
        } else if (task.equals("buy")){
          String productName = req.getString("productName");
          int quantity = req.getInt("quantity");
          boolean success = buyProduct(productName, quantity);
          if (success){
            res.put("ok", true);
            res.put("message", "Bought " + quantity + " " + productName);
          } else {
            res.put("ok", false);
            res.put("message", productName + " not available inventory");
          }
        } else {
          res.put("ok", false);
          res.put("message", "Task must be add, view, or buy");
        }
        res.put("type", "inventory");
      } catch (JSONException e){
        res.put("ok", false);
        res.put("message", "Invalid request format");
        e.printStackTrace();
      }
      return res;
  }

  private static void addProduct(String productName, int quantity){
    for (Product p : inventory){
      if (p.getName().equals(productName)){
        p.setQuantity(p.getQuantity() + quantity);
        return;
      }
    }
    inventory.add(new Product(productName, quantity));
  }

  private static boolean buyProduct(String productName, int quantity){
    for (Product p : inventory){
      if (p.getName().equals(productName)){
        if (p.getQuantity() >= quantity){
          p.setQuantity(p.getQuantity() - quantity);
          return true;
        }
      }
    }
    return false;
  }

  private static class Product {
    private String name;
    private int quantity;

    public Product(String name, int quantity){
      this.name = name;
      this.quantity = quantity;
    }

    public String getName(){
      return name;
    }

    public int getQuantity(){
      return quantity;
    }

    public void setQuantity(int quantity){
      this.quantity = quantity;
    }
  }


  // implement me in assignment 3
  static JSONObject roller(JSONObject req) {
    System.out.println("Roller request: " + req.toString());
    JSONObject res = new JSONObject();
    try{
      int dieCount = req.getInt("dieCount");
      int faces = req.getInt("faces");

      // check if >0
      if (dieCount <= 0 || faces <= 0){
        res.put("ok", false);
        res.put("message", "dieCount and faces must be greater than 0");
        return res;
      }

      JSONObject result = new JSONObject();
      for (int i = 0; i < dieCount; i++){
        int roll = (int) (Math.random() * faces) + 1;
        result.put(Integer.toString(roll), result.optInt(Integer.toString(roll, 0) + 1));
      }
      res.put("type", "roller");
      res.put("ok", true);
      res.put("result", result);
    } catch (JSONException e){
      res.put("ok", false);
      res.put("message", "dieCount and faces must be ints");
      e.printStackTrace();
    }
    return res;
  }

  // handles the simple addmany request
  static JSONObject addmany(JSONObject req){
    System.out.println("Add many request: " + req.toString());
    JSONObject res = testField(req, "nums");
    if (!res.getBoolean("ok")) {
      return res;
    }

    int result = 0;
    JSONArray array = req.getJSONArray("nums");
    for (int i = 0; i < array.length(); i ++){
      try{
        result += array.getInt(i);
      } catch (org.json.JSONException e){
        res.put("ok", false);
        res.put("message", "Values in array need to be ints");
        return res;
      }
    }

    res.put("ok", true);
    res.put("type", "addmany");
    res.put("result", result);
    return res;
  }

  // creates the error message for wrong type
  static JSONObject wrongType(JSONObject req){
    System.out.println("Wrong type request: " + req.toString());
    JSONObject res = new JSONObject();
    res.put("ok", false);
    res.put("message", "Type " + req.getString("type") + " is not supported.");
    return res;
  }

  // creates the error message for no given type
  static JSONObject noType(JSONObject req){
    System.out.println("No type request: " + req.toString());
    JSONObject res = new JSONObject();
    res.put("ok", false);
    res.put("message", "No request type was given.");
    return res;
  }

  // From: https://www.baeldung.com/java-validate-json-string
  public static JSONObject isValid(String json) {
    try {
      new JSONObject(json);
    } catch (JSONException e) {
      try {
        new JSONArray(json);
      } catch (JSONException ne) {
        JSONObject res = new JSONObject();
        res.put("ok", false);
        res.put("message", "req not JSON");
        return res;
      }
    }
    return new JSONObject();
  }

  // sends the response and closes the connection between client and server.
  static void overandout() {
    try {
      os.close();
      in.close();
      sock.close();
    } catch(Exception e) {e.printStackTrace();}

  }

  // sends the response and closes the connection between client and server.
  static void writeOut(JSONObject res) {
    try {
      os.writeUTF(res.toString());
      // make sure it wrote and doesn't get cached in a buffer
      os.flush();

    } catch(Exception e) {e.printStackTrace();}

  }
}