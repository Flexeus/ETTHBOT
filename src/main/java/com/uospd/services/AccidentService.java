package com.uospd.services;

import com.uospd.entityes.Accident;
import com.uospd.repositories.AccidentRepository;
import com.uospd.utils.Functions;
import com.uospd.utils.Network;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.uospd.utils.Functions.getDateDiff;
import static com.uospd.utils.Functions.timePassed;

@Service
@RequiredArgsConstructor
public class AccidentService{
    private final AccidentRepository accidentRepository;

    public String getUPSwitches(){
        List<Accident> upSwitches = accidentRepository.findAllByState(1)
                .stream()
                .filter(a -> getDateDiff(a.getDate(), new Date(), TimeUnit.MINUTES) < 60)
                .limit(30)
                .collect(Collectors.toList());
        return buildAccidentList("Поднялись:", upSwitches);
    }

    public String getDownSwitches(){
        List<Accident> downSwitches = accidentRepository.findAllByState(0);
        downSwitches = downSwitches.stream()
                .limit(30)
                .filter(x-> !Network.ping(x.getIp(),300))

                ///TODO: Сделать лучше
                .sorted(Comparator.comparingLong(x -> x.getDate().getTime()))
                .collect(Collectors.toList());
        return buildAccidentList("В аварии:", downSwitches);
    }

    private String buildAccidentList(String title, List<Accident> accidents){
        StringBuilder builder = new StringBuilder(title);
        if(accidents.isEmpty()){
            builder.append("\n").append("Список пуст");
            return builder.toString();
        }
        accidents.removeIf(c -> c.getStreet() == null || c.getHome() == null);
        builder.append(String.format("\n%-19s %-25s   %s\n", "IP", "Адрес", "Время"));
        List<String> ips = accidents.stream().map(x -> x.getIp().replace("10.42.", "..")).collect(Collectors.toList());

        var adresses = accidents.stream().map(x->{
            String str = x.getStreet();
            if(str.length() > 13) str = str.substring(0,12)+".";
            return str+","+x.getHome();
        }).collect(Collectors.toList());

        List<String> ipList = Functions.tableFormat(ips);
        List<String> adressesList = Functions.tableFormat(adresses);

        for(int i = 0;i < accidents.size();i++){
            Accident c = accidents.get(i);
            builder.append(ipList.get(i)).append("  ").append(adressesList.get(i)).append("  ").append(timePassed(c.getDate()));
            if(i!= accidents.size()-1) builder.append('\n');
        }
        ipList.clear();
        adressesList.clear();
        return builder.toString();
    }
}
