package com.kbms.taxonomy;

import com.kbms.audit.AuditService;
import com.kbms.common.ApiException;
import com.kbms.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {

    private final TagRepository tags;
    private final AuditService audit;

    public TagService(TagRepository tags, AuditService audit) {
        this.tags = tags;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<TagResponse> search(String search, int page, int size) {
        return PageResponse.of(
                tags.search(Objects.requireNonNullElse(Texts.blankToNull(search), ""), PageRequest.of(page, size, Sort.by("name"))),
                TagResponse::of);
    }

    @Transactional(readOnly = true)
    public List<TagResponse> all() {
        return tags.findAllByOrderByNameAsc().stream().map(TagResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public TagResponse get(long id) {
        return TagResponse.of(require(id));
    }

    @Transactional
    public TagResponse create(@Valid CreateRequest request) {
        String name = request.name().trim();
        if (tags.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A tag named '" + name + "' already exists");
        }
        Tag saved = tags.save(new Tag(name));
        audit.recordCurrentUser("TAG_CREATE", "TAG", String.valueOf(saved.getId()), name);
        return TagResponse.of(saved);
    }

    @Transactional
    public TagResponse update(long id, @Valid UpdateRequest request) {
        Tag tag = require(id);
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw ApiException.badRequest("Tag name cannot be blank");
        }
        tags.findByNameIgnoreCase(name)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw ApiException.conflict("A tag named '" + name + "' already exists");
                });
        tag.setName(name);
        tags.save(tag);
        audit.recordCurrentUser("TAG_UPDATE", "TAG", String.valueOf(id), name);
        return TagResponse.of(tag);
    }

    @Transactional
    public void delete(long id) {
        Tag tag = require(id);
        tags.delete(tag);
        audit.recordCurrentUser("TAG_DELETE", "TAG", String.valueOf(id), tag.getName());
    }

    @Transactional(readOnly = true)
    public Tag require(long id) {
        return tags.findById(id).orElseThrow(() -> ApiException.notFound("Tag " + id + " was not found"));
    }

    /** Resolves ids to tags, rejecting unknown ids so a stale form cannot silently drop tags. */
    @Transactional(readOnly = true)
    public List<Tag> resolveByIds(List<Long> ids) {
        var found = tags.findAllById(ids);
        if (found.size() != ids.stream().distinct().count()) {
            throw ApiException.badRequest("One or more tags do not exist");
        }
        return found;
    }

    public record CreateRequest(@NotBlank @Size(max = 80) String name) {}

    public record UpdateRequest(@NotBlank @Size(max = 80) String name) {}

    public record TagResponse(Long id, String name, Instant createdAt) {
        static TagResponse of(Tag tag) {
            return new TagResponse(tag.getId(), tag.getName(), tag.getCreatedAt());
        }
    }
}
