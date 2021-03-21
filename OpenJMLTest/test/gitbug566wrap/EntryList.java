public class EntryList {

    /*@ spec_public @*/ Object first;

    /*@ spec_public nullable @*/ EntryList rest;
    
    EntryList( Object first, EntryList rest ) {
    	this.first = first;
    	this.rest = rest;
    }
    
    // some methods, among them:

    /*@ normal_behavior
      @ requires rest == null;
      @ ensures \result == 1;
      @
      @ also
      @
      @ normal_behavior
      @ requires rest != null;
      @ ensures \result == \java_math(1 + rest.size());
      @*/
    /*@ spec_public pure code_java_math spec_java_math @*/ int size() {
	   return 1 + (rest == null ? 0 : rest.size());
    }
}