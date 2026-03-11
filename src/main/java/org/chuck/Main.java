package org.chuck;

import org.chuck.audio.*;
import org.chuck.compiler.*;
import org.chuck.core.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        new ChuckCLI().run(args);
    }
}
