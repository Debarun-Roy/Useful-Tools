package spike;
import com.password4j.*;
import com.password4j.types.Argon2;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdapterTest {
    @Test void argon2idRoundTrip() {
        var fn = Argon2Function.getInstance(19456, 2, 1, 32, Argon2.ID);
        var hash = Password.hash("synthetic-only-password").addRandomSalt(16).with(fn).getResult();
        assertTrue(hash.startsWith("$argon2id$"));
        assertTrue(Password.check("synthetic-only-password", hash).with(fn));
        assertFalse(Password.check("wrong-password", hash).with(fn));
    }
}
