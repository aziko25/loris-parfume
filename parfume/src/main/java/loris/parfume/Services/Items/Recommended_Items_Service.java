package loris.parfume.Services.Items;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import loris.parfume.DTOs.Requests.Items.RecommendedItemsRequest;
import loris.parfume.DTOs.returnDTOs.Recommended_Items_DTO;
import loris.parfume.Models.Items.Items;
import loris.parfume.Models.Items.Recommended_Items;
import loris.parfume.Repositories.Items.ItemsRepository;
import loris.parfume.Repositories.Items.Recommended_Items_Repository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class Recommended_Items_Service {

    private final Recommended_Items_Repository recommendedItemsRepository;
    private final ItemsRepository itemsRepository;

    @Value("${pageSize}")
    private Integer pageSize;

    @Transactional
    public List<Recommended_Items_DTO> create(RecommendedItemsRequest recommendedItemsRequest) {

        Items item = itemsRepository.findById(recommendedItemsRequest.getItemId())
                .orElseThrow(() -> new EntityNotFoundException("Item " + recommendedItemsRequest.getItemId() + " Not Found"));

        return setRecommendedItems(recommendedItemsRequest, item);
    }

    public Page<Recommended_Items_DTO> all(Integer page) {

        Pageable pageable = PageRequest.of(page - 1, pageSize);

        Page<Recommended_Items> recommendedItemsPage = recommendedItemsRepository.findAll(pageable);

        Map<Items, List<Items>> groupedItems = recommendedItemsPage.getContent().stream()
                .collect(Collectors.groupingBy(
                        Recommended_Items::getItem,
                        Collectors.mapping(Recommended_Items::getRecommendedItem, Collectors.toList())
                ));

        List<Recommended_Items_DTO> dtos = groupedItems.entrySet().stream()
                .map(entry -> new Recommended_Items_DTO(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, recommendedItemsPage.getTotalElements());
    }

    public Recommended_Items_DTO getById(Long itemId) {

        Items item = itemsRepository.findById(itemId).orElseThrow(() -> new EntityNotFoundException("Item Not Found"));

        List<Items> recommendedItemsList = recommendedItemsRepository.findAllByItem(item)
                .stream().map(Recommended_Items::getRecommendedItem)
                .collect(Collectors.toList());

        return new Recommended_Items_DTO(item, recommendedItemsList);
    }

    @Transactional
    public List<Recommended_Items_DTO> update(RecommendedItemsRequest recommendedItemsRequest) {

        Items item = itemsRepository.findById(recommendedItemsRequest.getItemId())
                .orElseThrow(() -> new EntityNotFoundException("Item " + recommendedItemsRequest.getItemId() + " Not Found"));

        recommendedItemsRepository.deleteAllByItem(item);

        return setRecommendedItems(recommendedItemsRequest, item);
    }

    private List<Recommended_Items_DTO> setRecommendedItems(RecommendedItemsRequest recommendedItemsRequest, Items item) {

        List<Recommended_Items> recommendedItemsList = new ArrayList<>();

        if (recommendedItemsRequest.getRecommendedItemsIds() == null || recommendedItemsRequest.getRecommendedItemsIds().isEmpty()) {

            return new ArrayList<>();
        }

        for (Long recommendedItemId : recommendedItemsRequest.getRecommendedItemsIds()) {

            if (!recommendedItemId.equals(item.getId())) {

                Items recommendedItemFound = itemsRepository.findById(recommendedItemId)
                        .orElseThrow(() -> new EntityNotFoundException("Recommended Item " + recommendedItemsRequest.getItemId() + " Not Found"));

                Recommended_Items recommendedItem = new Recommended_Items();

                recommendedItem.setItem(item);
                recommendedItem.setRecommendedItem(recommendedItemFound);

                recommendedItemsList.add(recommendedItem);
            }
        }

        recommendedItemsRepository.saveAll(recommendedItemsList);

        return recommendedItemsList.stream()
                .map(recommendedItem -> new Recommended_Items_DTO(recommendedItem.getItem(),
                        recommendedItemsList.stream()
                                .map(Recommended_Items::getRecommendedItem)
                                .collect(Collectors.toList())))
                .collect(Collectors.toList());
    }
}