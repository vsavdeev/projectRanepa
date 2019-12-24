package ru.ranepa.project;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.opencsv.CSVWriter;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.ranepa.project.storage.StorageFileNotFoundException;
import ru.ranepa.project.storage.StorageService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class FileUploadController {

    private final StorageService storageService;

    @Autowired
    public FileUploadController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/")
    public String listUploadedFiles(Model model) throws IOException {

        model.addAttribute("files", storageService.loadAll().map(
                path -> MvcUriComponentsBuilder.fromMethodName(FileUploadController.class,
                        "serveFile", path.getFileName().toString()).build().toString())
                .collect(Collectors.toList()));

        return "uploadForm";
    }

    @GetMapping("/files/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {

        Resource file = storageService.loadAsResource(filename);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + file.getFilename() + "\"").body(file);
    }

    @PostMapping("/")
    public String handleFileUpload(@RequestParam("file") MultipartFile[] files, RedirectAttributes redirectAttributes) throws IOException, InvalidFormatException {

        String name = "data.csv";
        String originalFileName = "data.csv";
        String contentType = "text/plain";
        File csv = new File(name);
        FileWriter outputfile = new FileWriter(csv);
        CSVWriter writer = new CSVWriter(outputfile, ';',
                CSVWriter.NO_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END);
        List<String[]> data = new ArrayList<String[]>();
        for (MultipartFile file : files) {
            XWPFDocument document = new XWPFDocument(file.getInputStream());
            XWPFWordExtractor extractor = new XWPFWordExtractor(document);
            String myString = extractor.getText();
            byte bytes[] = myString.getBytes("UTF-8");
            String value = new String(bytes, "UTF-8");
            String uniqueID = UUID.randomUUID().toString();
            data.add(new String[] { uniqueID, value });

        }
        writer.writeAll(data);
        byte[] bytes = Files.readAllBytes(csv.toPath());
        MultipartFile multipartFile = new MockMultipartFile(name,originalFileName,contentType,bytes);
        writer.close();
        storageService.store(multipartFile);

        redirectAttributes.addFlashAttribute("message",
                "You uploaded " + files.length + " files !");
        return "redirect:/";
    }

    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
        return ResponseEntity.notFound().build();
    }

}