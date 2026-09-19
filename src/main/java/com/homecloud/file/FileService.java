package com.homecloud.file;

import com.homecloud.file.dto.FileResponse;
import com.homecloud.user.User;
import com.homecloud.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class FileService {

    private FileRepository fileRepository;
    private UserRepository userRepository;

    public FileService(FileRepository fileRepository, UserRepository userRepository) {
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
    }

    public FileResponse createFile(String filename, String username){

        User user = userRepository.findByUsername(username)
                .orElseThrow(()-> new RuntimeException("User not found: " + username));

        String storagePath = user.getId()+"/"+ UUID.randomUUID();

        File file = new File(filename, storagePath, user);

        return new FileResponse(fileRepository.save(file));

    }

    public FileResponse getFile(Long fileId, String username){

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        File file = fileRepository.findByIdAndOwnerId(fileId, user.getId())
                .orElseThrow(() -> new RuntimeException("File not found: "));

        return new FileResponse(file);

    }

    public List<FileResponse> getMyFiles(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        return fileRepository.findAllByOwnerId(user.getId())
                .stream()
                .map(FileResponse::new)
                .toList();
    }

}
